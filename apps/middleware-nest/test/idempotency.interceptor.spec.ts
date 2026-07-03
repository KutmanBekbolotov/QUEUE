import { BadRequestException, ExecutionContext } from '@nestjs/common';
import { of } from 'rxjs';
import { IdempotencyInterceptor } from '../src/idempotency/idempotency.interceptor';

describe('IdempotencyInterceptor', () => {
  const interceptor = new IdempotencyInterceptor();
  const next = { handle: jest.fn(() => of({ ok: true })) };

  it('uses externalBookingId from body when header is absent', () => {
    const request = {
      method: 'POST',
      headers: {} as Record<string, string>,
      params: {},
      body: { externalBookingId: 'tunduk-123' },
    };

    interceptor.intercept(context(request), next);

    expect(request.headers['idempotency-key']).toBe('tunduk-123');
  });

  it('preserves explicit header instead of deriving from route params', () => {
    const request = {
      method: 'POST',
      headers: { 'idempotency-key': 'cancel-456' } as Record<string, string>,
      params: { externalId: 'tunduk-123' },
      body: {},
    };

    interceptor.intercept(context(request), next);

    expect(request.headers['idempotency-key']).toBe('cancel-456');
  });

  it('does not use route params as implicit idempotency keys', () => {
    const request = {
      method: 'POST',
      headers: {} as Record<string, string>,
      params: { externalId: 'tunduk-123' },
      body: {},
    };

    expect(() => interceptor.intercept(context(request), next)).toThrow(BadRequestException);
  });

  it('rejects mutating requests without idempotency material', () => {
    const request = {
      method: 'POST',
      headers: {} as Record<string, string>,
      params: {},
      body: {},
    };

    expect(() => interceptor.intercept(context(request), next)).toThrow(BadRequestException);
  });

  function context(request: unknown): ExecutionContext {
    return {
      switchToHttp: () => ({ getRequest: () => request }),
    } as ExecutionContext;
  }
});
