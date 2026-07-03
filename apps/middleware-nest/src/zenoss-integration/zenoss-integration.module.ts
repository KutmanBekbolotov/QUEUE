import { Module } from '@nestjs/common';
import { CrmIntegrationModule } from '../crm-integration/crm-integration.module';
import { ExternalAuthModule } from '../external-auth/external-auth.module';
import { IdempotencyModule } from '../idempotency/idempotency.module';
import { ZenossIntegrationController } from './zenoss-integration.controller';

@Module({
  imports: [CrmIntegrationModule, ExternalAuthModule, IdempotencyModule],
  controllers: [ZenossIntegrationController],
})
export class ZenossIntegrationModule {}
