import { Controller, Get } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';

@ApiTags('Health')
@Controller()
export class HealthController {
  @Get('/health')
  @ApiOperation({ summary: 'Middleware health check' })
  health(): Record<string, unknown> {
    return {
      status: 'UP',
      service: 'middleware-nest',
      timestamp: new Date().toISOString(),
    };
  }

  @Get('/external/health')
  @ApiOperation({ summary: 'External health check alias' })
  externalHealth(): Record<string, unknown> {
    return this.health();
  }
}
