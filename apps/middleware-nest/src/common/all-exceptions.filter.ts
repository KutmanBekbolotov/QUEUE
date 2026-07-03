import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
} from '@nestjs/common';
import { Request, Response } from 'express';
import { requestId } from './request-context';
import { ErrorResponse } from './error-response';

@Catch()
export class AllExceptionsFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost): void {
    const context = host.switchToHttp();
    const response = context.getResponse<Response>();
    const request = context.getRequest<Request>();

    const status = exception instanceof HttpException
      ? exception.getStatus()
      : HttpStatus.INTERNAL_SERVER_ERROR;

    const body = exception instanceof HttpException ? exception.getResponse() : undefined;
    const normalized = this.normalize(body, status);

    response.status(status).json({
      timestamp: new Date().toISOString(),
      requestId: requestId(request),
      code: normalized.code,
      message: normalized.message,
      details: normalized.details,
    } satisfies ErrorResponse);
  }

  private normalize(
    body: string | object | undefined,
    status: number,
  ): { code: string; message: string; details: Record<string, unknown> } {
    if (typeof body === 'object' && body !== null) {
      const payload = body as Record<string, unknown>;
      const message = Array.isArray(payload.message)
        ? 'Request validation failed'
        : String(payload.message ?? 'Request failed');
      return {
        code: String(payload.code ?? this.defaultCode(status)),
        message,
        details: Array.isArray(payload.message) ? { errors: payload.message } : {},
      };
    }
    return {
      code: this.defaultCode(status),
      message: typeof body === 'string' ? body : 'Unexpected server error',
      details: {},
    };
  }

  private defaultCode(status: number): string {
    if (status === HttpStatus.BAD_REQUEST) return 'VALIDATION_ERROR';
    if (status === HttpStatus.UNAUTHORIZED) return 'UNAUTHENTICATED';
    if (status === HttpStatus.FORBIDDEN) return 'FORBIDDEN';
    if (status === HttpStatus.TOO_MANY_REQUESTS) return 'RATE_LIMITED';
    return status >= 500 ? 'INTERNAL_ERROR' : 'REQUEST_ERROR';
  }
}

