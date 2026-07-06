import { UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Request } from 'express';
import { decode, verify } from 'jsonwebtoken';
import * as bcrypt from 'bcryptjs';
import { AuthDatabaseService } from '../src/auth/auth.database';
import { AuthService } from '../src/auth/auth.service';
import { AuthUser } from '../src/auth/auth.types';

describe('AuthService', () => {
  const jwtSecret = 'replace-with-at-least-32-characters-secret';
  const user: AuthUser = {
    id: '0d259eb7-2505-4aa1-9d4d-fc8f418f6111',
    username: 'admin',
    passwordHash: '',
    fullName: 'Bootstrap Admin',
    status: 'ACTIVE',
    tokenVersion: 7,
    roles: ['SUPER_ADMIN'],
    permissions: ['USER_READ', 'ROLE_READ'],
  };

  it('authenticates against local DB data and issues Spring-compatible JWT', async () => {
    const passwordHash = await bcrypt.hash('secret', 10);
    const authDatabase = database({
      findUserByUsername: jest.fn().mockResolvedValue({ ...user, passwordHash }),
    });
    const service = new AuthService(authDatabase, config());

    const response = await service.login({ username: 'admin', password: 'secret' }, request());

    expect(response.tokenType).toBe('Bearer');
    expect(response.refreshToken).toEqual(expect.any(String));
    expect(response.roles).toEqual(['SUPER_ADMIN']);
    expect(response.permissions).toEqual(['USER_READ', 'ROLE_READ']);
    expect(() => verify(response.accessToken, jwtSecret, { algorithms: ['HS256'] })).not.toThrow();
    expect(decode(response.accessToken)).toEqual(expect.objectContaining({
      sub: user.id,
      username: 'admin',
      tokenVersion: 7,
    }));
    expect(authDatabase.insertRefreshToken).toHaveBeenCalledWith(
      user.id,
      expect.any(String),
      expect.any(Date),
      '127.0.0.1',
    );
    expect(authDatabase.writeLoginAudit).toHaveBeenCalledWith(
      'admin',
      user.id,
      true,
      null,
      '127.0.0.1',
      'jest',
    );
  });

  it('rejects bad credentials and audits failure', async () => {
    const passwordHash = await bcrypt.hash('secret', 10);
    const authDatabase = database({
      findUserByUsername: jest.fn().mockResolvedValue({ ...user, passwordHash }),
    });
    const service = new AuthService(authDatabase, config());

    await expect(service.login({ username: 'admin', password: 'wrong' }, request()))
      .rejects.toBeInstanceOf(UnauthorizedException);
    expect(authDatabase.insertRefreshToken).not.toHaveBeenCalled();
    expect(authDatabase.writeLoginAudit).toHaveBeenCalledWith(
      'admin',
      user.id,
      false,
      'BAD_CREDENTIALS',
      '127.0.0.1',
      'jest',
    );
  });

  function config(): ConfigService {
    return {
      get: jest.fn((key: string) => {
        const values: Record<string, string> = {
          BACKEND_JWT_SECRET: jwtSecret,
          JWT_ACCESS_TOKEN_TTL_MS: '900000',
          JWT_REFRESH_TOKEN_TTL_MS: '1209600000',
        };
        return values[key];
      }),
    } as unknown as ConfigService;
  }

  function database(overrides: Partial<Record<keyof AuthDatabaseService, jest.Mock>> = {}): AuthDatabaseService {
    return {
      findUserByUsername: jest.fn(),
      findUserById: jest.fn(),
      insertRefreshToken: jest.fn().mockResolvedValue(undefined),
      findRefreshTokenForUpdate: jest.fn(),
      replaceRefreshToken: jest.fn(),
      revokeRefreshToken: jest.fn(),
      writeLoginAudit: jest.fn().mockResolvedValue(undefined),
      withTransaction: jest.fn(),
      onModuleDestroy: jest.fn(),
      ...overrides,
    } as unknown as AuthDatabaseService;
  }

  function request(): Request {
    return {
      headers: {
        'x-forwarded-for': '127.0.0.1',
        'user-agent': 'jest',
      },
      socket: {},
    } as unknown as Request;
  }
});
