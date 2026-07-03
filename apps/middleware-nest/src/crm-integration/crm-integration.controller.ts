import { Body, Controller, Get, Param, Post, Query, Req, UseGuards, UseInterceptors } from '@nestjs/common';
import { ApiBody, ApiHeader, ApiOperation, ApiParam, ApiSecurity, ApiTags } from '@nestjs/swagger';
import { Request } from 'express';
import {
  CreateCrmBookingDto,
  CreateCrmTicketDto,
  CrmBookingSlotsQueryDto,
} from '../common/external.dto';
import { ExternalAuthGuard } from '../external-auth/external-auth.guard';
import { IdempotencyInterceptor } from '../idempotency/idempotency.interceptor';
import { CrmIntegrationService } from './crm-integration.service';

@Controller('/external/crm')
@UseGuards(ExternalAuthGuard)
@ApiTags('Generic CRM Integration')
@ApiSecurity('external-api-key')
@ApiHeader({ name: 'X-Integration-Client', required: false, description: 'CRM client code, for example CRM_MAIN, CRM_EXTERNAL, or NEW_CRM' })
@ApiHeader({ name: 'X-Request-Id', required: false, description: 'Request id forwarded to Spring' })
export class CrmIntegrationController {
  constructor(private readonly crmIntegrationService: CrmIntegrationService) {}

  @Post('/tickets')
  @UseInterceptors(IdempotencyInterceptor)
  @ApiOperation({ summary: 'Create a CRM ticket' })
  @ApiHeader({ name: 'Idempotency-Key', required: false, description: 'Required unless X-External-Request-Id or externalId is provided' })
  @ApiHeader({ name: 'X-External-Request-Id', required: false })
  @ApiBody({ type: CreateCrmTicketDto })
  createTicket(@Body() body: CreateCrmTicketDto, @Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.createTicket(body, request);
  }

  @Get('/tickets/:id')
  @ApiOperation({ summary: 'Get a CRM-created ticket by internal id' })
  @ApiParam({ name: 'id', format: 'uuid' })
  ticket(@Param('id') id: string, @Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.ticket(id, request);
  }

  @Get('/tickets/:id/status')
  @ApiOperation({ summary: 'Get a CRM-created ticket status' })
  @ApiParam({ name: 'id', format: 'uuid' })
  ticketStatus(@Param('id') id: string, @Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.ticketStatus(id, request);
  }

  @Post('/bookings')
  @UseInterceptors(IdempotencyInterceptor)
  @ApiOperation({ summary: 'Create a CRM booking' })
  @ApiHeader({ name: 'Idempotency-Key', required: false, description: 'Required unless X-External-Request-Id or externalId is provided' })
  @ApiHeader({ name: 'X-External-Request-Id', required: false })
  @ApiBody({ type: CreateCrmBookingDto })
  createBooking(@Body() body: CreateCrmBookingDto, @Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.createBooking(body, request);
  }

  @Post('/bookings/:id/cancel')
  @UseInterceptors(IdempotencyInterceptor)
  @ApiOperation({ summary: 'Cancel a CRM booking by internal UUID or CRM external id' })
  @ApiParam({ name: 'id' })
  @ApiHeader({ name: 'Idempotency-Key', required: false })
  @ApiHeader({ name: 'X-External-Request-Id', required: false })
  cancelBooking(@Param('id') id: string, @Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.cancelBooking(id, request);
  }

  @Get('/bookings/:id/status')
  @ApiOperation({ summary: 'Get a CRM booking status by internal UUID or CRM external id' })
  @ApiParam({ name: 'id' })
  bookingStatus(@Param('id') id: string, @Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.bookingStatus(id, request);
  }

  @Get('/directories/departments')
  @ApiOperation({ summary: 'List departments allowed for CRM integration' })
  departments(@Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.departments(request);
  }

  @Get('/directories/services')
  @ApiOperation({ summary: 'List services allowed for CRM integration' })
  services(@Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.services(request);
  }

  @Get('/booking/slots')
  @ApiOperation({ summary: 'List available booking slots for CRM integration' })
  slots(@Query() query: CrmBookingSlotsQueryDto, @Req() request: Request): Promise<unknown> {
    return this.crmIntegrationService.slots(query, request);
  }
}
