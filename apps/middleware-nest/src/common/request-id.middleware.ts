import { Injectable, NestMiddleware } from '@nestjs/common';
import { randomUUID } from 'crypto';
import { NextFunction, Request, Response } from 'express';

export const REQUEST_ID_HEADER = 'x-request-id';
export const CORRELATION_ID_HEADER = 'x-correlation-id';

@Injectable()
export class RequestIdMiddleware implements NestMiddleware {
  use(req: Request, res: Response, next: NextFunction): void {
    const requestId = this.header(req, REQUEST_ID_HEADER) ?? randomUUID();
    const correlationId = this.header(req, CORRELATION_ID_HEADER) ?? requestId;

    req.headers[REQUEST_ID_HEADER] = requestId;
    req.headers[CORRELATION_ID_HEADER] = correlationId;
    res.setHeader('X-Request-Id', requestId);
    res.setHeader('X-Correlation-Id', correlationId);
    next();
  }

  private header(req: Request, name: string): string | undefined {
    const value = req.headers[name];
    if (Array.isArray(value)) {
      return value[0];
    }
    return value && value.trim().length > 0 ? value : undefined;
  }
}

