import { Injectable } from '@nestjs/common';
import { ThrottlerGuard } from '@nestjs/throttler';

@Injectable()
export class IntegrationThrottlerGuard extends ThrottlerGuard {
  protected override async getTracker(req: Record<string, any>): Promise<string> {
    const value = req.headers?.['x-integration-client'];
    const clientCode = Array.isArray(value) ? value[0] : value;
    if (typeof clientCode === 'string' && clientCode.trim().length > 0) {
      return `integration:${clientCode.trim().toUpperCase()}`;
    }
    return super.getTracker(req);
  }
}
