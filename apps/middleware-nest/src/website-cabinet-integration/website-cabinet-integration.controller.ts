import { Body, Controller, Get, Param, Post, Query, Req, UseGuards, UseInterceptors } from '@nestjs/common';
import { ApiBody, ApiHeader, ApiOperation, ApiParam, ApiSecurity, ApiTags } from '@nestjs/swagger';
import { Request } from 'express';
import { BackendClientService } from '../backend-client/backend-client.service';
import {
  AvailableDatesQueryDto,
  BookingSlotsQueryDto,
  CreateExternalBookingDto,
} from '../common/external.dto';
import { ExternalAuthGuard } from '../external-auth/external-auth.guard';
import { IdempotencyInterceptor } from '../idempotency/idempotency.interceptor';

@Controller('/external/cabinet')
@UseGuards(ExternalAuthGuard)
@ApiTags('Website Cabinet Integration')
@ApiSecurity('external-api-key')
@ApiHeader({ name: 'X-Request-Id', required: false, description: 'Request id forwarded to Spring' })
export class WebsiteCabinetIntegrationController {
  constructor(private readonly backendClient: BackendClientService) {}

  @Get('/regions')
  @ApiOperation({ summary: 'List regions for Website Cabinet' })
  regions(@Req() request: Request): Promise<unknown> {
    return this.backendClient.get('/api/v1/regions', request, 'WEBSITE_CABINET');
  }

  @Get('/departments')
  @ApiOperation({ summary: 'List departments for Website Cabinet' })
  departments(@Req() request: Request): Promise<unknown> {
    return this.backendClient.get('/api/v1/departments', request, 'WEBSITE_CABINET');
  }

  @Get('/services')
  @ApiOperation({ summary: 'List services for Website Cabinet' })
  services(@Req() request: Request): Promise<unknown> {
    return this.backendClient.get('/api/v1/services', request, 'WEBSITE_CABINET');
  }

  @Get('/booking/available-dates')
  @ApiOperation({ summary: 'List available booking dates for Website Cabinet' })
  availableDates(@Query() query: AvailableDatesQueryDto, @Req() request: Request): Promise<unknown> {
    return this.backendClient.get('/api/v1/booking/available-dates', request, 'WEBSITE_CABINET', { ...query });
  }

  @Get('/booking/slots')
  @ApiOperation({ summary: 'List available booking slots for Website Cabinet' })
  slots(@Query() query: BookingSlotsQueryDto, @Req() request: Request): Promise<unknown> {
    return this.backendClient.get('/api/v1/booking/slots', request, 'WEBSITE_CABINET', { ...query });
  }

  @Post('/booking')
  @UseInterceptors(IdempotencyInterceptor)
  @ApiOperation({ summary: 'Create a Website Cabinet booking' })
  @ApiHeader({ name: 'Idempotency-Key', required: false, description: 'Required for mutating external requests unless X-External-Request-Id or externalBookingId is provided' })
  @ApiHeader({ name: 'X-External-Request-Id', required: false })
  @ApiBody({ type: CreateExternalBookingDto })
  createBooking(@Body() body: CreateExternalBookingDto, @Req() request: Request): Promise<unknown> {
    return this.backendClient.post('/api/v1/booking', {
      ...body,
      externalId: body.externalBookingId,
      source: 'WEBSITE_CABINET',
    }, request, 'WEBSITE_CABINET');
  }

  @Post('/booking/:id/cancel')
  @UseInterceptors(IdempotencyInterceptor)
  @ApiOperation({ summary: 'Cancel a Website Cabinet booking' })
  @ApiParam({ name: 'id', format: 'uuid' })
  @ApiHeader({ name: 'Idempotency-Key', required: false })
  @ApiHeader({ name: 'X-External-Request-Id', required: false })
  cancelBooking(@Param('id') id: string, @Req() request: Request): Promise<unknown> {
    return this.backendClient.post(`/api/v1/booking/${id}/cancel`, { id, source: 'WEBSITE_CABINET' }, request, 'WEBSITE_CABINET');
  }

  @Get('/booking/:id/status')
  @ApiOperation({ summary: 'Get Website Cabinet booking status' })
  @ApiParam({ name: 'id', format: 'uuid' })
  bookingStatus(@Param('id') id: string, @Req() request: Request): Promise<unknown> {
    return this.backendClient.get(`/api/v1/booking/${id}`, request, 'WEBSITE_CABINET');
  }
}
