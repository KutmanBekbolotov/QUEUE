import { Module } from '@nestjs/common';
import { SafeLoggerService } from './safe-logger.service';

@Module({
  providers: [SafeLoggerService],
  exports: [SafeLoggerService],
})
export class SafeLoggerModule {}

