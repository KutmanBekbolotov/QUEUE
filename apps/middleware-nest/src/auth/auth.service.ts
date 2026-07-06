import {
  ForbiddenException,
  Injectable,
  InternalServerErrorException,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Algorithm, JwtPayload, sign, verify } from 'jsonwebtoken';
import { createHash, randomBytes } from 'crypto';
import { Request } from 'express';
import * as bcrypt from 'bcryptjs';
import { headerValue } from '../common/request-context';
import {
  AuthResponseDto,
  LoginRequestDto,
  LogoutRequestDto,
  MeResponseDto,
  RefreshRequestDto,
} from './auth.dto';
import { AuthDatabaseService } from './auth.database';
import { AuthUser } from './auth.types';

@Injectable()
export class AuthService {
  private readonly jwtSecret: string;
  private readonly accessTokenTtlMs: number;
  private readonly refreshTokenTtlMs: number;

  constructor(
    private readonly authDatabase: AuthDatabaseService,
    configService: ConfigService,
  ) {
    this.jwtSecret = configService.get<string>('BACKEND_JWT_SECRET')
      ?? 'replace-with-at-least-32-characters-secret';
    this.accessTokenTtlMs = Number(configService.get<string | number>('JWT_ACCESS_TOKEN_TTL_MS') ?? 15 * 60_000);
    this.refreshTokenTtlMs = Number(configService.get<string | number>('JWT_REFRESH_TOKEN_TTL_MS') ?? 14 * 24 * 60 * 60_000);
  }

  async login(body: LoginRequestDto, request: Request): Promise<AuthResponseDto> {
    const user = await this.authDatabase.findUserByUsername(body.username);
    if (!user || !(await bcrypt.compare(body.password, user.passwordHash))) {
      await this.auditLogin(body.username, user, false, 'BAD_CREDENTIALS', request);
      throw new UnauthorizedException({
        code: 'BAD_CREDENTIALS',
        message: 'Invalid username or password',
      });
    }
    if (user.status !== 'ACTIVE') {
      await this.auditLogin(body.username, user, false, 'USER_NOT_ACTIVE', request);
      throw new ForbiddenException({
        code: 'USER_NOT_ACTIVE',
        message: 'User is not active',
      });
    }

    const refresh = this.createRefreshToken();
    await this.authDatabase.insertRefreshToken(
      user.id,
      refresh.hash,
      this.refreshExpiresAt(),
      this.remoteIp(request),
    );
    await this.auditLogin(user.username, user, true, null, request);

    return this.authResponse(user, refresh.rawToken);
  }

  async refresh(body: RefreshRequestDto, request: Request): Promise<AuthResponseDto> {
    const tokenHash = this.hash(body.refreshToken);
    return this.authDatabase.withTransaction(async (client) => {
      const existing = await this.authDatabase.findRefreshTokenForUpdate(tokenHash, client);
      const now = new Date();
      if (!existing || existing.revokedAt || existing.expiresAt <= now) {
        throw new UnauthorizedException({
          code: 'INVALID_REFRESH_TOKEN',
          message: 'Invalid refresh token',
        });
      }

      const user = await this.authDatabase.findUserById(existing.userId, client);
      if (!user) {
        throw new UnauthorizedException({
          code: 'INVALID_REFRESH_TOKEN',
          message: 'Refresh token user no longer exists',
        });
      }
      if (user.status !== 'ACTIVE') {
        throw new ForbiddenException({
          code: 'USER_NOT_ACTIVE',
          message: 'User is not active',
        });
      }

      const replacement = this.createRefreshToken();
      await this.authDatabase.insertRefreshToken(
        user.id,
        replacement.hash,
        this.refreshExpiresAt(),
        this.remoteIp(request),
        client,
      );
      await this.authDatabase.replaceRefreshToken(tokenHash, replacement.hash, client);
      return this.authResponse(user, replacement.rawToken);
    });
  }

  async logout(body: LogoutRequestDto): Promise<void> {
    await this.authDatabase.revokeRefreshToken(this.hash(body.refreshToken));
  }

  async me(request: Request): Promise<MeResponseDto> {
    const token = this.bearerToken(request);
    if (!token) {
      throw new UnauthorizedException({
        code: 'UNAUTHENTICATED',
        message: 'Missing bearer token',
      });
    }

    const claims = this.verifyAccessToken(token);
    if (!claims.sub || typeof claims.tokenVersion !== 'number') {
      throw this.invalidToken();
    }

    const user = await this.authDatabase.findUserById(claims.sub);
    if (!user || user.status !== 'ACTIVE' || user.tokenVersion !== claims.tokenVersion) {
      throw this.invalidToken();
    }
    return this.meResponse(user);
  }

  private authResponse(user: AuthUser, refreshToken: string): AuthResponseDto {
    const accessToken = this.createAccessToken(user);
    const expiresAt = new Date(Date.now() + this.accessTokenTtlMs).toISOString();
    return {
      accessToken,
      refreshToken,
      tokenType: 'Bearer',
      expiresAt,
      roles: user.roles,
      permissions: user.permissions,
    };
  }

  private meResponse(user: AuthUser): MeResponseDto {
    return {
      id: user.id,
      username: user.username,
      fullName: user.fullName,
      status: user.status,
      roles: user.roles,
      permissions: user.permissions,
    };
  }

  private createAccessToken(user: AuthUser): string {
    this.assertJwtSecret();
    const nowSeconds = Math.floor(Date.now() / 1000);
    return sign(
      {
        sub: user.id,
        username: user.username,
        tokenVersion: user.tokenVersion,
        iat: nowSeconds,
        exp: nowSeconds + Math.floor(this.accessTokenTtlMs / 1000),
      },
      this.jwtSecret,
      { algorithm: this.jwtAlgorithm() },
    );
  }

  private verifyAccessToken(token: string): JwtPayload {
    this.assertJwtSecret();
    try {
      const claims = verify(token, this.jwtSecret, {
        algorithms: [this.jwtAlgorithm()],
      });
      if (typeof claims === 'string') {
        throw this.invalidToken();
      }
      return claims;
    } catch {
      throw this.invalidToken();
    }
  }

  private createRefreshToken(): { rawToken: string; hash: string } {
    const rawToken = randomBytes(64).toString('base64url');
    return { rawToken, hash: this.hash(rawToken) };
  }

  private hash(value: string): string {
    return createHash('sha256').update(value, 'utf8').digest('base64url');
  }

  private refreshExpiresAt(): Date {
    return new Date(Date.now() + this.refreshTokenTtlMs);
  }

  private bearerToken(request: Request): string | undefined {
    const authorization = headerValue(request, 'authorization');
    return authorization?.startsWith('Bearer ') ? authorization.slice(7) : undefined;
  }

  private remoteIp(request: Request): string | null {
    const forwarded = headerValue(request, 'x-forwarded-for')?.split(',')[0]?.trim();
    const ip = forwarded ?? request.ip ?? request.socket?.remoteAddress;
    return ip?.replace(/^::ffff:/, '') ?? null;
  }

  private userAgent(request: Request): string | null {
    return headerValue(request, 'user-agent') ?? null;
  }

  private async auditLogin(
    username: string,
    user: AuthUser | null,
    success: boolean,
    reason: string | null,
    request: Request,
  ): Promise<void> {
    await this.authDatabase.writeLoginAudit(
      username,
      user?.id ?? null,
      success,
      reason,
      this.remoteIp(request),
      this.userAgent(request),
    );
  }

  private jwtAlgorithm(): Algorithm {
    const bytes = Buffer.byteLength(this.jwtSecret, 'utf8');
    if (bytes >= 64) {
      return 'HS512';
    }
    if (bytes >= 48) {
      return 'HS384';
    }
    return 'HS256';
  }

  private assertJwtSecret(): void {
    if (Buffer.byteLength(this.jwtSecret, 'utf8') < 32) {
      throw new InternalServerErrorException({
        code: 'JWT_SECRET_TOO_SHORT',
        message: 'BACKEND_JWT_SECRET must be at least 32 bytes',
      });
    }
  }

  private invalidToken(): UnauthorizedException {
    return new UnauthorizedException({
      code: 'INVALID_TOKEN',
      message: 'Invalid or expired access token',
    });
  }
}
