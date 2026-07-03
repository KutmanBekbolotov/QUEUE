import { Body, Controller, Get, Param, Post, Query, Req, UseGuards, UseInterceptors } from '@nestjs/common';
import { ApiBody, ApiHeader, ApiOperation, ApiParam, ApiSecurity, ApiTags } from '@nestjs/swagger';
import { Request } from 'express';
import {
  CreateCrmBookingDto,
  CreateCrmTicketDto,
  CrmBookingSlotsQueryDto,
} from '../common/external.dto';
import { CrmIntegrationService } from '../crm-integration/crm-integration.service';
import { ExternalAuthGuard } from '../external-auth/external-auth.guard';
import { IdempotencyInterceptor } from '../idempotency/idempotency.interceptor';

const ZENOSS_ALIAS_CLIENT = 'ZENOSS';

@Controller('/external/zenoss')
@UseGuards(ExternalAuthGuard)
@ApiTags('Deprecated Zenoss Compatibility')
@ApiSecurity('external-api-key')
@ApiHeader({ name: 'X-Request-Id', required: false, description: 'Request id forwarded to Spring' })
export class ZenossIntegrationController {
  constructor(private readonly crmIntegrationService: CrmIntegrationService) {}

  // Deprecated: use POST /external/crm/tickets with X-Integration-Client instead.
  @Post('/tickets')
  @UseInterceptors(IdempotencyInterceptor)
  @ApiOperation({ summary: 'Deprecated alias for creating a CRM ticket' })
  @ApiHeader({ name: 'Idempotency-Key', required: false, description: 'Required unless X-External-Request-Id or externalId is provided' })
  @ApiHeader({ name: 'X-External-Request-Id', required: false })
  @ApiBody({ type: CreateCrmTicketDto })
  createTicket(@Body() body: CreateCrmTicketDto, @Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.createTicket(body, request, ZENOSS_ALIAS_CLIENT);
  }

  // Deprecated: use GET /external/crm/tickets/:id instead.
  @Get('/tickets/:id')
  @ApiOperation({ summary: 'Deprecated alias for getting a CRM ticket' })
  @ApiParam({ name: 'id', format: 'uuid' })
  ticket(@Param('id') id: string, @Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.ticket(id, request, ZENOSS_ALIAS_CLIENT);
  }

  // Deprecated: use GET /external/crm/tickets/:id/status instead.
  @Get('/tickets/:id/status')
  @ApiOperation({ summary: 'Deprecated alias for getting a CRM ticket status' })
  @ApiParam({ name: 'id', format: 'uuid' })
  ticketStatus(@Param('id') id: string, @Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.ticketStatus(id, request, ZENOSS_ALIAS_CLIENT);
  }

  // Deprecated: use POST /external/crm/bookings instead.
  @Post('/bookings')
  @UseInterceptors(IdempotencyInterceptor)
  @ApiOperation({ summary: 'Deprecated alias for creating a CRM booking' })
  @ApiHeader({ name: 'Idempotency-Key', required: false, description: 'Required unless X-External-Request-Id or externalId is provided' })
  @ApiHeader({ name: 'X-External-Request-Id', required: false })
  @ApiBody({ type: CreateCrmBookingDto })
  createBooking(@Body() body: CreateCrmBookingDto, @Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.createBooking(body, request, ZENOSS_ALIAS_CLIENT);
  }

  // Deprecated: use POST /external/crm/bookings/:id/cancel instead.
  @Post('/bookings/:id/cancel')
  @UseInterceptors(IdempotencyInterceptor)
  @ApiOperation({ summary: 'Deprecated alias for cancelling a CRM booking' })
  @ApiParam({ name: 'id' })
  @ApiHeader({ name: 'Idempotency-Key', required: false })
  @ApiHeader({ name: 'X-External-Request-Id', required: false })
  cancelBooking(@Param('id') id: string, @Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.cancelBooking(id, request, ZENOSS_ALIAS_CLIENT);
  }

  // Deprecated: use GET /external/crm/bookings/:id/status instead.
  @Get('/bookings/:id/status')
  @ApiOperation({ summary: 'Deprecated alias for getting a CRM booking status' })
  @ApiParam({ name: 'id' })
  bookingStatus(@Param('id') id: string, @Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.bookingStatus(id, request, ZENOSS_ALIAS_CLIENT);
  }

  // Deprecated: use GET /external/crm/booking/slots instead.
  @Get('/booking/slots')
  @ApiOperation({ summary: 'Deprecated alias for listing CRM booking slots' })
  slots(@Query() query: CrmBookingSlotsQueryDto, @Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.slots(query, request, ZENOSS_ALIAS_CLIENT);
  }

  // Deprecated: use GET /external/crm/directories/departments instead.
  @Get('/directories/departments')
  @ApiOperation({ summary: 'Deprecated alias for listing CRM departments' })
  departments(@Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.departments(request, ZENOSS_ALIAS_CLIENT);
  }

  // Deprecated: use GET /external/crm/directories/services instead.
  @Get('/directories/services')
  @ApiOperation({ summary: 'Deprecated alias for listing CRM services' })
  services(@Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.services(request, ZENOSS_ALIAS_CLIENT);
  }
}
