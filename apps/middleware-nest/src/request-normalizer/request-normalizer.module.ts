import { Module } from '@nestjs/common';
import { RequestNormalizerInterceptor } from './request-normalizer.interceptor';

@Module({
  providers: [RequestNormalizerInterceptor],
  exports: [RequestNormalizerInterceptor],
})
export class RequestNormalizerModule {}

