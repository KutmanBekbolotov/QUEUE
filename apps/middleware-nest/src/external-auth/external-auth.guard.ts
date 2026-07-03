import { CanActivate, ExecutionContext, ForbiddenException, Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Request } from 'express';
import { headerValue } from '../common/request-context';

@Injectable()
export class ExternalAuthGuard implements CanActivate {
  constructor(private readonly configService: ConfigService) {}

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<Request>();
    const token = this.token(request);
    const clientCode = this.clientCode(request);
    const clientKeys = this.clientApiKeys();

    if (clientKeys.size > 0) {
      const allowedForClient = clientKeys.get(clientCode) ?? new Set<string>();
      if (!token || !allowedForClient.has(token)) {
        throw new UnauthorizedException({
          code: 'EXTERNAL_AUTH_FAILED',
          message: 'External client authentication failed',
        });
      }
    } else if (!token || !this.legacyApiKeys().has(token)) {
      throw new UnauthorizedException({
        code: 'EXTERNAL_AUTH_FAILED',
        message: 'External client authentication failed',
      });
    }

    this.requireAllowedIp(clientCode, request);
    request.headers['x-integration-client'] = clientCode;
    return true;
  }

  private token(request: Request): string | undefined {
    const apiKey = headerValue(request, 'x-api-key');
    if (apiKey) {
      return apiKey;
    }
    const authorization = headerValue(request, 'authorization');
    if (authorization?.startsWith('Bearer ')) {
      return authorization.slice(7);
    }
    return undefined;
  }

  private clientCode(request: Request): string {
    const header = headerValue(request, 'x-integration-client');
    if (header && header.trim().length > 0) {
      return header.trim().toUpperCase();
    }
    const path = request.path ?? request.url ?? '';
    if (path.startsWith('/external/tunduk')) {
      return 'TUNDUK';
    }
    if (path.startsWith('/external/cabinet')) {
      return 'WEBSITE_CABINET';
    }
    if (path.startsWith('/external/zenoss')) {
      return 'ZENOSS';
    }
    return 'CRM_MAIN';
  }

  private legacyApiKeys(): Set<string> {
    return new Set(this.configService
      .get<string>('EXTERNAL_API_KEYS', '')
      .split(',')
      .map((value) => value.trim())
      .filter(Boolean));
  }

  private clientApiKeys(): Map<string, Set<string>> {
    return this.clientMap(this.configService.get<string>('EXTERNAL_CLIENT_API_KEYS', ''));
  }

  private requireAllowedIp(clientCode: string, request: Request): void {
    const allowedIps = this.clientMap(this.configService.get<string>('EXTERNAL_CLIENT_ALLOWED_IPS', ''));
    const allowedForClient = allowedIps.get(clientCode);
    if (!allowedForClient || allowedForClient.size === 0) {
      return;
    }
    const ip = this.remoteIp(request);
    if (!ip || !allowedForClient.has(ip)) {
      throw new ForbiddenException({
        code: 'EXTERNAL_IP_FORBIDDEN',
        message: 'External client IP is not allowed',
      });
    }
  }

  private clientMap(raw: string): Map<string, Set<string>> {
    const result = new Map<string, Set<string>>();
    raw.split(',')
      .map((entry) => entry.trim())
      .filter(Boolean)
      .forEach((entry) => {
        const [clientCode, values] = entry.split(':', 2);
        if (!clientCode || !values) {
          return;
        }
        result.set(
          clientCode.trim().toUpperCase(),
          new Set(values.split('|').map((value) => value.trim()).filter(Boolean)),
        );
      });
    return result;
  }

  private remoteIp(request: Request): string | undefined {
    const forwarded = headerValue(request, 'x-forwarded-for')?.split(',')[0]?.trim();
    const ip = forwarded ?? request.ip ?? request.socket?.remoteAddress;
    return ip?.replace(/^::ffff:/, '');
  }
}
