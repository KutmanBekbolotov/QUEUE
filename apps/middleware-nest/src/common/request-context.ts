import { Request } from 'express';
import { CORRELATION_ID_HEADER, REQUEST_ID_HEADER } from './request-id.middleware';

export function requestId(req: Request): string {
  const value = req.headers[REQUEST_ID_HEADER];
  return Array.isArray(value) ? value[0] : value ?? '';
}

export function correlationId(req: Request): string {
  const value = req.headers[CORRELATION_ID_HEADER];
  return Array.isArray(value) ? value[0] : value ?? requestId(req);
}

export function headerValue(req: Request, name: string): string | undefined {
  const value = req.headers[name.toLowerCase()];
  return Array.isArray(value) ? value[0] : value;
}

