import { SafeLoggerService } from '../src/safe-logger/safe-logger.service';

describe('SafeLoggerService', () => {
  it('masks PII and secrets recursively', () => {
    const logger = new SafeLoggerService();

    const masked = logger.mask({
      citizenFullName: 'Person Name',
      citizenPin: '12345678901234',
      nested: {
        phone: '+996700000000',
        token: 'secret',
        'x-api-key': 'api-secret',
        'X-Backend-Integration-Key': 'backend-secret',
        IdempotencyKey: 'idem-1',
        safe: 'visible',
      },
    }) as Record<string, unknown>;

    expect(masked.citizenFullName).toBe('[MASKED]');
    expect(masked.citizenPin).toBe('[MASKED]');
    expect(masked.nested).toMatchObject({
      phone: '[MASKED]',
      token: '[MASKED]',
      'x-api-key': '[MASKED]',
      'X-Backend-Integration-Key': '[MASKED]',
      IdempotencyKey: '[MASKED]',
      safe: 'visible',
    });
  });
});
