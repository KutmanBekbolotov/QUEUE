import { Module } from '@nestjs/common';
import { ExternalAuthGuard } from './external-auth.guard';

@Module({
  providers: [ExternalAuthGuard],
  exports: [ExternalAuthGuard],
})
export class ExternalAuthModule {}

