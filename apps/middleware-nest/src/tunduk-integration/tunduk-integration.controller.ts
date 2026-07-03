import { Body, Controller, Get, Param, Post, Query, Req, UseGuards, UseInterceptors } from '@nestjs/common';
import { ApiBody, ApiHeader, ApiOperation, ApiParam, ApiSecurity, ApiTags } from '@nestjs/swagger';
import { Request } from 'express';
import { BackendClientService } from '../backend-client/backend-client.service';
import { BookingSlotsQueryDto, CreateExternalBookingDto } from '../common/external.dto';
import { ExternalAuthGuard } from '../external-auth/external-auth.guard';
import { IdempotencyInterceptor } from '../idempotency/idempotency.interceptor';

@Controller('/external/tunduk')
@UseGuards(ExternalAuthGuard)
@ApiTags('Tunduk Integration')
@ApiSecurity('external-api-key')
@ApiHeader({ name: 'X-Request-Id', required: false, description: 'Request id forwarded to Spring' })
export class TundukIntegrationController {
  constructor(private readonly backendClient: BackendClientService) {}

  @Post('/bookings')
  @UseInterceptors(IdempotencyInterceptor)
  @ApiOperation({ summary: 'Create a Tunduk booking' })
  @ApiHeader({ name: 'Idempotency-Key', required: false, description: 'Required for mutating external requests unless X-External-Request-Id or externalBookingId is provided' })
  @ApiHeader({ name: 'X-External-Request-Id', required: false })
  @ApiBody({ type: CreateExternalBookingDto })
  createBooking(@Body() body: CreateExternalBookingDto, @Req() request: Request): Promise<unknown> {
    return this.backendClient.post('/api/v1/booking', {
      ...body,
      externalId: body.externalBookingId,
      source: 'TUNDUK',
    }, request, 'TUNDUK');
  }

  @Post('/bookings/:externalId/cancel')
  @UseInterceptors(IdempotencyInterceptor)
  @ApiOperation({ summary: 'Cancel a Tunduk booking by external id' })
  @ApiParam({ name: 'externalId' })
  @ApiHeader({ name: 'Idempotency-Key', required: false })
  @ApiHeader({ name: 'X-External-Request-Id', required: false })
  cancelBooking(@Param('externalId') externalId: string, @Req() request: Request): Promise<unknown> {
    return this.backendClient.post(`/api/v1/booking/external/TUNDUK/${externalId}/cancel`, { externalId, source: 'TUNDUK' }, request, 'TUNDUK');
  }

  @Get('/bookings/:externalId/status')
  @ApiOperation({ summary: 'Get Tunduk booking status by external id' })
  @ApiParam({ name: 'externalId' })
  bookingStatus(@Param('externalId') externalId: string, @Req() request: Request): Promise<unknown> {
    return this.backendClient.get(`/api/v1/booking/external/TUNDUK/${externalId}`, request, 'TUNDUK');
  }

  @Get('/booking/slots')
  @ApiOperation({ summary: 'List available booking slots for Tunduk' })
  slots(@Query() query: BookingSlotsQueryDto, @Req() request: Request): Promise<unknown> {
    return this.backendClient.get('/api/v1/booking/slots', request, 'TUNDUK', { ...query, source: 'TUNDUK' });
  }

  @Get('/directories/departments')
  @ApiOperation({ summary: 'List departments for Tunduk' })
  departments(@Req() request: Request): Promise<unknown> {
    return this.backendClient.get('/api/v1/departments', request, 'TUNDUK');
  }

  @Get('/directories/services')
  @ApiOperation({ summary: 'List services for Tunduk' })
  services(@Req() request: Request): Promise<unknown> {
    return this.backendClient.get('/api/v1/services', request, 'TUNDUK');
  }
}
