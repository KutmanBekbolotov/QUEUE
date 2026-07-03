import { MiddlewareConsumer, Module, NestModule } from '@nestjs/common';
import { APP_FILTER, APP_GUARD, APP_INTERCEPTOR } from '@nestjs/core';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { ThrottlerModule } from '@nestjs/throttler';
import { AllExceptionsFilter } from './common/all-exceptions.filter';
import { RequestIdMiddleware } from './common/request-id.middleware';
import { BackendClientModule } from './backend-client/backend-client.module';
import { HealthModule } from './health/health.module';
import { SafeLoggerModule } from './safe-logger/safe-logger.module';
import { SafeLoggingInterceptor } from './safe-logger/safe-logging.interceptor';
import { RequestNormalizerModule } from './request-normalizer/request-normalizer.module';
import { RequestNormalizerInterceptor } from './request-normalizer/request-normalizer.interceptor';
import { IdempotencyModule } from './idempotency/idempotency.module';
import { ExternalAuthModule } from './external-auth/external-auth.module';
import { IntegrationThrottlerGuard } from './external-auth/integration-throttler.guard';
import { CrmIntegrationModule } from './crm-integration/crm-integration.module';
import { ZenossIntegrationModule } from './zenoss-integration/zenoss-integration.module';
import { TundukIntegrationModule } from './tunduk-integration/tunduk-integration.module';
import { WebsiteCabinetIntegrationModule } from './website-cabinet-integration/website-cabinet-integration.module';
import { AuthProxyModule } from './auth-proxy/auth-proxy.module';
import { BookingProxyModule } from './booking-proxy/booking-proxy.module';
import { DirectoryProxyModule } from './directory-proxy/directory-proxy.module';
import { StatusProxyModule } from './status-proxy/status-proxy.module';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    ThrottlerModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => [
        {
          ttl: config.get<number>('RATE_LIMIT_TTL_MS') ?? 60_000,
          limit: config.get<number>('RATE_LIMIT_MAX') ?? 120,
        },
      ],
    }),
    BackendClientModule,
    HealthModule,
    SafeLoggerModule,
    RequestNormalizerModule,
    IdempotencyModule,
    ExternalAuthModule,
    CrmIntegrationModule,
    ZenossIntegrationModule,
    TundukIntegrationModule,
    WebsiteCabinetIntegrationModule,
    AuthProxyModule,
    BookingProxyModule,
    DirectoryProxyModule,
    StatusProxyModule,
  ],
  providers: [
    { provide: APP_GUARD, useClass: IntegrationThrottlerGuard },
    { provide: APP_FILTER, useClass: AllExceptionsFilter },
    { provide: APP_INTERCEPTOR, useClass: RequestNormalizerInterceptor },
    { provide: APP_INTERCEPTOR, useClass: SafeLoggingInterceptor },
  ],
})
export class AppModule implements NestModule {
  configure(consumer: MiddlewareConsumer): void {
    consumer.apply(RequestIdMiddleware).forRoutes('*');
  }
}
