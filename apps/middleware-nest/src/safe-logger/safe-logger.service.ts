import { Injectable, Logger } from '@nestjs/common';

const SENSITIVE_KEY_PATTERNS = [
  'password',
  'secret',
  'key',
  'apikey',
  'backendintegrationkey',
  'integrationkey',
  'authorization',
  'token',
  'accessToken',
  'refreshToken',
  'fullName',
  'citizenFullName',
  'pin',
  'citizenPin',
  'phone',
  'citizenPhone',
].map((key) => key.toLowerCase());

@Injectable()
export class SafeLoggerService {
  private readonly logger = new Logger('middleware');

  info(message: string, meta: Record<string, unknown> = {}): void {
    this.logger.log(JSON.stringify({ message, ...this.maskRecord(meta) }));
  }

  warn(message: string, meta: Record<string, unknown> = {}): void {
    this.logger.warn(JSON.stringify({ message, ...this.maskRecord(meta) }));
  }

  error(message: string, meta: Record<string, unknown> = {}): void {
    this.logger.error(JSON.stringify({ message, ...this.maskRecord(meta) }));
  }

  mask(value: unknown): unknown {
    if (Array.isArray(value)) {
      return value.map((item) => this.mask(item));
    }
    if (value && typeof value === 'object') {
      return Object.fromEntries(
        Object.entries(value as Record<string, unknown>).map(([key, child]) => [
          key,
          this.shouldMask(key) ? '[MASKED]' : this.mask(child),
        ]),
      );
    }
    return value;
  }

  private shouldMask(key: string): boolean {
    const normalized = key.toLowerCase().replace(/[^a-z0-9]/g, '');
    return SENSITIVE_KEY_PATTERNS.some((pattern) => normalized.includes(pattern));
  }

  private maskRecord(meta: Record<string, unknown>): Record<string, unknown> {
    return this.mask(meta) as Record<string, unknown>;
  }
}
