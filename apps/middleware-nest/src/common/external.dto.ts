import { Type } from 'class-transformer';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsBoolean,
  IsDateString,
  IsIn,
  IsInt,
  IsNotEmpty,
  IsObject,
  IsOptional,
  IsString,
  IsUUID,
  Max,
  Min,
  ValidateNested,
} from 'class-validator';

const REPORT_TYPES = [
  'SUMMARY',
  'BY_REGION',
  'BY_DEPARTMENT',
  'BY_EMPLOYEE',
  'BY_SERVICE',
  'BY_SOURCE',
  'BY_STATUS',
  'WAITING_TIME',
  'SERVICE_TIME',
  'CANCELLATIONS',
  'NO_SHOWS',
  'BOOKINGS',
  'WINDOW_WORKLOAD',
  'WORKLOAD_HOURLY',
  'WORKLOAD_DAILY',
  'TICKETS_DETAIL',
  'BOOKINGS_DETAIL',
  'INTEGRATIONS',
] as const;

const REPORT_FORMATS = ['CSV', 'XLSX', 'PDF'] as const;
const TICKET_SOURCES = ['TERMINAL', 'QR_SELF_SERVICE', 'WEBSITE_CABINET', 'TUNDUK', 'CRM', 'CRM_ZENOSS', 'ADMIN_CREATED'] as const;
const TICKET_STATUSES = ['CREATED', 'WAITING', 'CALLED', 'IN_SERVICE', 'PAUSED', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'EXPIRED', 'TRANSFERRED'] as const;
const BOOKING_STATUSES = ['CREATED', 'CONFIRMED', 'CHECKED_IN', 'CANCELLED', 'EXPIRED', 'NO_SHOW'] as const;

export class CreateExternalTicketDto {
  @ApiProperty({ format: 'uuid', example: '0d259eb7-2505-4aa1-9d4d-fc8f418f6111' })
  @IsUUID()
  departmentId!: string;

  @ApiProperty({ format: 'uuid', example: '28cb3a2a-915e-44e0-bc7e-61e5511c8f3f' })
  @IsUUID()
  serviceId!: string;

  @ApiPropertyOptional({ example: 'Masked in logs' })
  @IsOptional()
  @IsString()
  citizenFullName?: string;

  @ApiPropertyOptional({ example: '12345678901234' })
  @IsOptional()
  @IsString()
  citizenPin?: string;

  @ApiPropertyOptional({ example: '+996700000000' })
  @IsOptional()
  @IsString()
  citizenPhone?: string;

  @ApiPropertyOptional({ example: 'zenoss-ticket-123' })
  @IsOptional()
  @IsString()
  externalTicketId?: string;
}

export class CreateExternalBookingDto {
  @ApiProperty({ format: 'uuid', example: '0d259eb7-2505-4aa1-9d4d-fc8f418f6111' })
  @IsUUID()
  departmentId!: string;

  @ApiProperty({ format: 'uuid', example: '28cb3a2a-915e-44e0-bc7e-61e5511c8f3f' })
  @IsUUID()
  serviceId!: string;

  @ApiProperty({ format: 'uuid', example: '05bb3c61-bccc-4220-898d-ee7cf57433a0' })
  @IsUUID()
  slotId!: string;

  @ApiProperty({ example: 'cabinet-booking-123' })
  @IsNotEmpty()
  @IsString()
  externalBookingId!: string;

  @ApiPropertyOptional({ example: 'Masked in logs' })
  @IsOptional()
  @IsString()
  citizenFullName?: string;

  @ApiPropertyOptional({ example: '12345678901234' })
  @IsOptional()
  @IsString()
  citizenPin?: string;

  @ApiPropertyOptional({ example: '+996700000000' })
  @IsOptional()
  @IsString()
  citizenPhone?: string;

  @ApiPropertyOptional({ example: '01KG123ABC' })
  @IsOptional()
  @IsString()
  vehicleNumber?: string;
}

export class CreateCrmTicketDto {
  @ApiPropertyOptional({ format: 'uuid', example: '0d259eb7-2505-4aa1-9d4d-fc8f418f6111' })
  @IsOptional()
  @IsUUID()
  departmentId?: string;

  @ApiPropertyOptional({ example: 'BISHKEK_MAIN' })
  @IsOptional()
  @IsString()
  departmentCode?: string;

  @ApiPropertyOptional({ format: 'uuid', example: '28cb3a2a-915e-44e0-bc7e-61e5511c8f3f' })
  @IsOptional()
  @IsUUID()
  serviceId?: string;

  @ApiPropertyOptional({ example: 'DRIVER_LICENSE_REPLACE' })
  @IsOptional()
  @IsString()
  serviceCode?: string;

  @ApiPropertyOptional({ example: 'CRM-REQ-1001' })
  @IsOptional()
  @IsString()
  externalId?: string;

  @ApiPropertyOptional({ example: 'CRM-REQ-1001', deprecated: true })
  @IsOptional()
  @IsString()
  externalTicketId?: string;

  @ApiPropertyOptional({ example: 'Masked in logs' })
  @IsOptional()
  @IsString()
  citizenFullName?: string;

  @ApiPropertyOptional({ example: '12345678901234' })
  @IsOptional()
  @IsString()
  citizenPin?: string;

  @ApiPropertyOptional({ example: '+996700000000' })
  @IsOptional()
  @IsString()
  citizenPhone?: string;

  @ApiPropertyOptional({ type: Object })
  @IsOptional()
  @IsObject()
  metadata?: Record<string, unknown>;
}

export class CreateCrmBookingDto {
  @ApiPropertyOptional({ format: 'uuid', example: '0d259eb7-2505-4aa1-9d4d-fc8f418f6111' })
  @IsOptional()
  @IsUUID()
  departmentId?: string;

  @ApiPropertyOptional({ example: 'BISHKEK_MAIN' })
  @IsOptional()
  @IsString()
  departmentCode?: string;

  @ApiPropertyOptional({ format: 'uuid', example: '28cb3a2a-915e-44e0-bc7e-61e5511c8f3f' })
  @IsOptional()
  @IsUUID()
  serviceId?: string;

  @ApiPropertyOptional({ example: 'DRIVER_LICENSE_REPLACE' })
  @IsOptional()
  @IsString()
  serviceCode?: string;

  @ApiProperty({ format: 'uuid', example: '05bb3c61-bccc-4220-898d-ee7cf57433a0' })
  @IsUUID()
  slotId!: string;

  @ApiPropertyOptional({ example: 'CRM-BOOKING-1001' })
  @IsOptional()
  @IsString()
  externalId?: string;

  @ApiPropertyOptional({ example: 'CRM-BOOKING-1001', deprecated: true })
  @IsOptional()
  @IsString()
  externalBookingId?: string;

  @ApiPropertyOptional({ example: 'Masked in logs' })
  @IsOptional()
  @IsString()
  citizenFullName?: string;

  @ApiPropertyOptional({ example: '12345678901234' })
  @IsOptional()
  @IsString()
  citizenPin?: string;

  @ApiPropertyOptional({ example: '+996700000000' })
  @IsOptional()
  @IsString()
  citizenPhone?: string;

  @ApiPropertyOptional({ example: '01KG123ABC' })
  @IsOptional()
  @IsString()
  vehicleNumber?: string;

  @ApiPropertyOptional({ type: Object })
  @IsOptional()
  @IsObject()
  metadata?: Record<string, unknown>;
}

export class BookingSlotsQueryDto {
  @ApiProperty({ format: 'uuid', example: '0d259eb7-2505-4aa1-9d4d-fc8f418f6111' })
  @IsUUID()
  departmentId!: string;

  @ApiProperty({ format: 'uuid', example: '28cb3a2a-915e-44e0-bc7e-61e5511c8f3f' })
  @IsUUID()
  serviceId!: string;

  @ApiProperty({ format: 'date', example: '2026-07-02' })
  @IsDateString()
  date!: string;

  @ApiPropertyOptional({ enum: ['WEBSITE_CABINET', 'TUNDUK', 'CRM_ZENOSS'] })
  @IsOptional()
  @IsIn(['WEBSITE_CABINET', 'TUNDUK', 'CRM_ZENOSS'])
  source?: 'WEBSITE_CABINET' | 'TUNDUK' | 'CRM_ZENOSS';
}

export class CrmBookingSlotsQueryDto {
  @ApiPropertyOptional({ format: 'uuid', example: '0d259eb7-2505-4aa1-9d4d-fc8f418f6111' })
  @IsOptional()
  @IsUUID()
  departmentId?: string;

  @ApiPropertyOptional({ example: 'BISHKEK_MAIN' })
  @IsOptional()
  @IsString()
  departmentCode?: string;

  @ApiPropertyOptional({ format: 'uuid', example: '28cb3a2a-915e-44e0-bc7e-61e5511c8f3f' })
  @IsOptional()
  @IsUUID()
  serviceId?: string;

  @ApiPropertyOptional({ example: 'DRIVER_LICENSE_REPLACE' })
  @IsOptional()
  @IsString()
  serviceCode?: string;

  @ApiProperty({ format: 'date', example: '2026-07-02' })
  @IsDateString()
  date!: string;
}

export class AvailableDatesQueryDto {
  @ApiProperty({ format: 'uuid', example: '0d259eb7-2505-4aa1-9d4d-fc8f418f6111' })
  @IsUUID()
  departmentId!: string;

  @ApiProperty({ format: 'uuid', example: '28cb3a2a-915e-44e0-bc7e-61e5511c8f3f' })
  @IsUUID()
  serviceId!: string;

  @ApiPropertyOptional({ format: 'date', example: '2026-07-01' })
  @IsOptional()
  @IsDateString()
  fromDate?: string;

  @ApiPropertyOptional({ format: 'date', example: '2026-07-31' })
  @IsOptional()
  @IsDateString()
  toDate?: string;

  @ApiPropertyOptional({ enum: ['WEBSITE_CABINET', 'TUNDUK', 'CRM_ZENOSS'] })
  @IsOptional()
  @IsIn(['WEBSITE_CABINET', 'TUNDUK', 'CRM_ZENOSS'])
  source?: 'WEBSITE_CABINET' | 'TUNDUK' | 'CRM_ZENOSS';
}

export class ReportFilterQueryDto {
  @ApiProperty({ format: 'date', example: '2026-07-01' })
  @IsDateString()
  dateFrom!: string;

  @ApiProperty({ format: 'date', example: '2026-07-31' })
  @IsDateString()
  dateTo!: string;

  @ApiPropertyOptional({ format: 'uuid' })
  @IsOptional()
  @IsUUID()
  regionId?: string;

  @ApiPropertyOptional({ format: 'uuid' })
  @IsOptional()
  @IsUUID()
  departmentId?: string;

  @ApiPropertyOptional({ format: 'uuid' })
  @IsOptional()
  @IsUUID()
  employeeId?: string;

  @ApiPropertyOptional({ format: 'uuid' })
  @IsOptional()
  @IsUUID()
  windowId?: string;

  @ApiPropertyOptional({ format: 'uuid' })
  @IsOptional()
  @IsUUID()
  serviceCategoryId?: string;

  @ApiPropertyOptional({ format: 'uuid' })
  @IsOptional()
  @IsUUID()
  serviceId?: string;

  @ApiPropertyOptional({ enum: TICKET_SOURCES })
  @IsOptional()
  @IsIn(TICKET_SOURCES)
  source?: string;

  @ApiPropertyOptional({ enum: TICKET_STATUSES })
  @IsOptional()
  @IsIn(TICKET_STATUSES)
  ticketStatus?: string;

  @ApiPropertyOptional({ enum: BOOKING_STATUSES })
  @IsOptional()
  @IsIn(BOOKING_STATUSES)
  bookingStatus?: string;

  @ApiPropertyOptional({ format: 'uuid' })
  @IsOptional()
  @IsUUID()
  cancellationReasonId?: string;

  @ApiPropertyOptional({ example: 'department' })
  @IsOptional()
  @IsString()
  groupBy?: string;

  @ApiPropertyOptional({ default: false })
  @IsOptional()
  @IsBoolean()
  @Type(() => Boolean)
  includePersonalData?: boolean;

  @ApiPropertyOptional({ default: 0, minimum: 0 })
  @IsOptional()
  @IsInt()
  @Min(0)
  @Type(() => Number)
  page?: number;

  @ApiPropertyOptional({ default: 50, minimum: 1, maximum: 500 })
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(500)
  @Type(() => Number)
  size?: number;
}

export class ReportExportRequestDto {
  @ApiProperty({ enum: REPORT_TYPES, example: 'BY_DEPARTMENT' })
  @IsIn(REPORT_TYPES)
  reportType!: string;

  @ApiProperty({ enum: REPORT_FORMATS, example: 'CSV' })
  @IsIn(REPORT_FORMATS)
  format!: string;

  @ApiProperty({ type: ReportFilterQueryDto })
  @ValidateNested()
  @Type(() => ReportFilterQueryDto)
  filters!: ReportFilterQueryDto;
}
