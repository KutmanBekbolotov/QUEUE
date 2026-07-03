import {
  BadRequestException,
  CallHandler,
  ExecutionContext,
  Injectable,
  NestInterceptor,
} from '@nestjs/common';
import { Request } from 'express';
import { Observable } from 'rxjs';
import { headerValue } from '../common/request-context';

const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

@Injectable()
export class IdempotencyInterceptor implements NestInterceptor {
  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const request = context.switchToHttp().getRequest<Request>();
    if (MUTATING_METHODS.has(request.method)) {
      const key = headerValue(request, 'idempotency-key')
        ?? headerValue(request, 'x-external-request-id')
        ?? this.requestExternalId(request);
      if (!key || key.trim().length === 0) {
        throw new BadRequestException({
          code: 'IDEMPOTENCY_KEY_REQUIRED',
          message: 'Idempotency-Key or X-External-Request-Id header is required',
        });
      }
      request.headers['idempotency-key'] = key;
    }
    return next.handle();
  }

  private requestExternalId(request: Request): string | undefined {
    const body = request.body as Record<string, unknown> | undefined;
    const value = (typeof body?.externalBookingId === 'string' ? body.externalBookingId : undefined)
      ?? (typeof body?.externalTicketId === 'string' ? body.externalTicketId : undefined)
      ?? (typeof body?.externalId === 'string' ? body.externalId : undefined);
    return value && value.trim().length > 0 ? value : undefined;
  }
}
