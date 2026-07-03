import { Injectable, NestMiddleware } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { randomUUID } from 'crypto';
import { NextFunction, Request, Response } from 'express';

const FRONTEND_PROXY_PREFIXES = [
  '/api/v1',
  '/auth',
  '/audit-logs',
  '/booking',
  '/departments',
  '/devices',
  '/employees',
  '/halls',
  '/operator',
  '/permissions',
  '/regions',
  '/reports',
  '/roles',
  '/rooms',
  '/service-categories',
  '/service-windows',
  '/services',
  '/terminal',
  '/tickets',
  '/tv',
  '/users',
  '/windows',
];

const SKIPPED_REQUEST_HEADERS = new Set([
  'connection',
  'content-length',
  'host',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
  'x-api-key',
  'x-backend-integration-key',
  'x-integration-client',
]);

const SKIPPED_RESPONSE_HEADERS = new Set([
  'access-control-allow-credentials',
  'access-control-allow-headers',
  'access-control-allow-methods',
  'access-control-allow-origin',
  'access-control-expose-headers',
  'connection',
  'content-encoding',
  'content-length',
  'keep-alive',
  'transfer-encoding',
]);

@Injectable()
export class FrontendProxyMiddleware implements NestMiddleware {
  private readonly backendBaseUrl: string;

  constructor(configService: ConfigService) {
    this.backendBaseUrl = (configService.get<string>('BACKEND_BASE_URL') ?? 'http://localhost:8080').replace(/\/+$/, '');
  }

  async use(req: Request, res: Response, next: NextFunction): Promise<void> {
    const target = this.targetUrl(req);
    if (!target) {
      next();
      return;
    }

    try {
      const response = await fetch(target, {
        method: req.method,
        headers: this.requestHeaders(req),
        body: await this.requestBody(req),
        redirect: 'manual',
      });

      res.status(response.status);
      response.headers.forEach((value, name) => {
        if (!SKIPPED_RESPONSE_HEADERS.has(name.toLowerCase())) {
          res.setHeader(name, value);
        }
      });

      if (req.method.toUpperCase() === 'HEAD' || response.status === 204 || response.status === 304) {
        res.end();
        return;
      }

      res.send(Buffer.from(await response.arrayBuffer()));
    } catch {
      res.status(503).json({
        timestamp: new Date().toISOString(),
        requestId: this.singleHeader(req.headers['x-request-id']) ?? randomUUID(),
        code: 'BACKEND_UNAVAILABLE',
        message: 'Backend service is unavailable',
        details: {},
      });
    }
  }

  private targetUrl(req: Request): string | undefined {
    const originalUrl = req.originalUrl || req.url;
    const queryStart = originalUrl.indexOf('?');
    const path = queryStart >= 0 ? originalUrl.slice(0, queryStart) : originalUrl;
    const query = queryStart >= 0 ? originalUrl.slice(queryStart) : '';

    if (!this.shouldProxy(path)) {
      return undefined;
    }

    const backendPath = path.startsWith('/api/v1/') || path === '/api/v1'
      ? path
      : `/api/v1${path}`;
    return `${this.backendBaseUrl}${backendPath}${query}`;
  }

  private shouldProxy(path: string): boolean {
    return FRONTEND_PROXY_PREFIXES.some((prefix) => path === prefix || path.startsWith(`${prefix}/`));
  }

  private requestHeaders(req: Request): Headers {
    const headers = new Headers();
    Object.entries(req.headers).forEach(([name, value]) => {
      if (SKIPPED_REQUEST_HEADERS.has(name.toLowerCase()) || value === undefined) {
        return;
      }
      headers.set(name, Array.isArray(value) ? value.join(',') : String(value));
    });

    headers.set('X-Request-Id', this.singleHeader(req.headers['x-request-id']) ?? randomUUID());
    headers.set(
      'X-Correlation-Id',
      this.singleHeader(req.headers['x-correlation-id'])
        ?? this.singleHeader(req.headers['x-request-id'])
        ?? randomUUID(),
    );
    return headers;
  }

  private async requestBody(req: Request): Promise<BodyInit | undefined> {
    const method = req.method.toUpperCase();
    if (method === 'GET' || method === 'HEAD') {
      return undefined;
    }

    const body = (req as Request & { body?: unknown }).body;
    if (body !== undefined) {
      if (Buffer.isBuffer(body)) {
        return body as unknown as BodyInit;
      }
      if (typeof body === 'string') {
        return body;
      }
      return JSON.stringify(body);
    }

    const chunks: Buffer[] = [];
    for await (const chunk of req) {
      chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
    }
    return chunks.length > 0 ? Buffer.concat(chunks) as unknown as BodyInit : undefined;
  }

  private singleHeader(value: string | string[] | undefined): string | undefined {
    return Array.isArray(value) ? value[0] : value;
  }
}
