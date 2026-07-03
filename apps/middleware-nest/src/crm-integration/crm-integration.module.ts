import { Module } from '@nestjs/common';
import { BackendClientModule } from '../backend-client/backend-client.module';
import { ExternalAuthModule } from '../external-auth/external-auth.module';
import { IdempotencyModule } from '../idempotency/idempotency.module';
import { CrmIntegrationController } from './crm-integration.controller';
import { CrmIntegrationService } from './crm-integration.service';

@Module({
  imports: [BackendClientModule, ExternalAuthModule, IdempotencyModule],
  controllers: [CrmIntegrationController],
  providers: [CrmIntegrationService],
  exports: [CrmIntegrationService],
})
export class CrmIntegrationModule {}
