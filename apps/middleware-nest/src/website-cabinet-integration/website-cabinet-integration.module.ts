import { Module } from '@nestjs/common';
import { BackendClientModule } from '../backend-client/backend-client.module';
import { ExternalAuthModule } from '../external-auth/external-auth.module';
import { IdempotencyModule } from '../idempotency/idempotency.module';
import { WebsiteCabinetIntegrationController } from './website-cabinet-integration.controller';

@Module({
  imports: [BackendClientModule, ExternalAuthModule, IdempotencyModule],
  controllers: [WebsiteCabinetIntegrationController],
})
export class WebsiteCabinetIntegrationModule {}

