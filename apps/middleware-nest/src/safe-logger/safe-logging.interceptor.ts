import {
  CallHandler,
  ExecutionContext,
  Injectable,
  NestInterceptor,
} from '@nestjs/common';
import { Request, Response } from 'express';
import { Observable, tap } from 'rxjs';
import { requestId } from '../common/request-context';
import { SafeLoggerService } from './safe-logger.service';

@Injectable()
export class SafeLoggingInterceptor implements NestInterceptor {
  constructor(private readonly safeLogger: SafeLoggerService) {}

  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const http = context.switchToHttp();
    const request = http.getRequest<Request>();
    const response = http.getResponse<Response>();
    const started = Date.now();

    return next.handle().pipe(
      tap({
        next: () => this.log(request, response, started),
        error: () => this.log(request, response, started),
      }),
    );
  }

  private log(request: Request, response: Response, started: number): void {
    this.safeLogger.info('request completed', {
      requestId: requestId(request),
      method: request.method,
      path: request.originalUrl,
      statusCode: response.statusCode,
      durationMs: Date.now() - started,
    });
  }
}

