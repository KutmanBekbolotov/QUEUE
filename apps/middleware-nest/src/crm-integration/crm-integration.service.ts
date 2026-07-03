import { BadRequestException, Injectable } from '@nestjs/common';
import { Request } from 'express';
import { BackendClientService } from '../backend-client/backend-client.service';
import { headerValue } from '../common/request-context';
import {
  CreateCrmBookingDto,
  CreateCrmTicketDto,
  CrmBookingSlotsQueryDto,
} from '../common/external.dto';

const DEFAULT_CRM_CLIENT = 'CRM_MAIN';
const CRM_SOURCE = 'CRM';

type EntityType = 'DEPARTMENT' | 'SERVICE' | 'REGION';

interface MappingResolveResponse {
  internalId: string;
}

interface DirectoryReference {
  id?: string;
  code?: string;
  entityType: EntityType;
  fieldName: string;
}

@Injectable()
export class CrmIntegrationService {
  constructor(private readonly backendClient: BackendClientService) {}

  async createTicket(body: CreateCrmTicketDto, request: Request, fallbackClientCode = DEFAULT_CRM_CLIENT): Promise<unknown> {
    const clientCode = this.clientCode(request, fallbackClientCode);
    const departmentId = await this.resolveDirectoryId(clientCode, request, {
      id: body.departmentId,
      code: body.departmentCode,
      entityType: 'DEPARTMENT',
      fieldName: 'department',
    });
    const serviceId = await this.resolveDirectoryId(clientCode, request, {
      id: body.serviceId,
      code: body.serviceCode,
      entityType: 'SERVICE',
      fieldName: 'service',
    });
    const externalId = this.externalId(body.externalId, body.externalTicketId);

    return this.backendClient.post('/api/v1/tickets', {
      departmentId,
      serviceId,
      citizenFullName: body.citizenFullName,
      citizenPin: body.citizenPin,
      citizenPhone: body.citizenPhone,
      source: CRM_SOURCE,
      externalId,
    }, request, clientCode);
  }

  async createBooking(body: CreateCrmBookingDto, request: Request, fallbackClientCode = DEFAULT_CRM_CLIENT): Promise<unknown> {
    const clientCode = this.clientCode(request, fallbackClientCode);
    const departmentId = await this.resolveDirectoryId(clientCode, request, {
      id: body.departmentId,
      code: body.departmentCode,
      entityType: 'DEPARTMENT',
      fieldName: 'department',
    });
    const serviceId = await this.resolveDirectoryId(clientCode, request, {
      id: body.serviceId,
      code: body.serviceCode,
      entityType: 'SERVICE',
      fieldName: 'service',
    });
    const externalId = this.externalId(body.externalId, body.externalBookingId);

    return this.backendClient.post('/api/v1/booking', {
      departmentId,
      serviceId,
      slotId: body.slotId,
      citizenFullName: body.citizenFullName,
      citizenPin: body.citizenPin,
      citizenPhone: body.citizenPhone,
      vehicleNumber: body.vehicleNumber,
      source: CRM_SOURCE,
      externalId,
    }, request, clientCode);
  }

  ticket(id: string, request: Request, fallbackClientCode = DEFAULT_CRM_CLIENT): Promise<unknown> {
    const clientCode = this.clientCode(request, fallbackClientCode);
    return this.backendClient.get(`/api/v1/tickets/${id}`, request, clientCode);
  }

  ticketStatus(id: string, request: Request, fallbackClientCode = DEFAULT_CRM_CLIENT): Promise<unknown> {
    return this.ticket(id, request, fallbackClientCode);
  }

  bookingStatus(id: string, request: Request, fallbackClientCode = DEFAULT_CRM_CLIENT): Promise<unknown> {
    const clientCode = this.clientCode(request, fallbackClientCode);
    if (this.isUuid(id)) {
      return this.backendClient.get(`/api/v1/booking/${id}`, request, clientCode);
    }
    return this.backendClient.get(`/api/v1/booking/external/${CRM_SOURCE}/${id}`, request, clientCode);
  }

  cancelBooking(id: string, request: Request, fallbackClientCode = DEFAULT_CRM_CLIENT): Promise<unknown> {
    const clientCode = this.clientCode(request, fallbackClientCode);
    if (this.isUuid(id)) {
      return this.backendClient.post(`/api/v1/booking/${id}/cancel`, { source: CRM_SOURCE, externalId: id }, request, clientCode);
    }
    return this.backendClient.post(`/api/v1/booking/external/${CRM_SOURCE}/${id}/cancel`, {
      source: CRM_SOURCE,
      externalId: id,
    }, request, clientCode);
  }

  departments(request: Request, fallbackClientCode = DEFAULT_CRM_CLIENT): Promise<unknown> {
    return this.backendClient.get('/api/v1/departments', request, this.clientCode(request, fallbackClientCode));
  }

  services(request: Request, fallbackClientCode = DEFAULT_CRM_CLIENT): Promise<unknown> {
    return this.backendClient.get('/api/v1/services', request, this.clientCode(request, fallbackClientCode));
  }

  async slots(query: CrmBookingSlotsQueryDto, request: Request, fallbackClientCode = DEFAULT_CRM_CLIENT): Promise<unknown> {
    const clientCode = this.clientCode(request, fallbackClientCode);
    const departmentId = await this.resolveDirectoryId(clientCode, request, {
      id: query.departmentId,
      code: query.departmentCode,
      entityType: 'DEPARTMENT',
      fieldName: 'department',
    });
    const serviceId = await this.resolveDirectoryId(clientCode, request, {
      id: query.serviceId,
      code: query.serviceCode,
      entityType: 'SERVICE',
      fieldName: 'service',
    });

    return this.backendClient.get('/api/v1/booking/slots', request, clientCode, {
      departmentId,
      serviceId,
      date: query.date,
      source: CRM_SOURCE,
    });
  }

  clientCode(request: Request, fallbackClientCode = DEFAULT_CRM_CLIENT): string {
    const value = headerValue(request, 'x-integration-client') ?? fallbackClientCode;
    return value.trim().toUpperCase();
  }

  private async resolveDirectoryId(clientCode: string, request: Request, reference: DirectoryReference): Promise<string> {
    if (reference.id && reference.id.trim().length > 0) {
      return reference.id;
    }
    if (!reference.code || reference.code.trim().length === 0) {
      throw new BadRequestException({
        code: 'DIRECTORY_REFERENCE_REQUIRED',
        message: `${reference.fieldName}Id or ${reference.fieldName}Code is required`,
      });
    }
    const result = await this.backendClient.get<MappingResolveResponse>('/api/v1/integration-mappings/resolve', request, clientCode, {
      clientCode,
      entityType: reference.entityType,
      externalCode: reference.code,
    });
    return result.internalId;
  }

  private externalId(primary?: string, legacy?: string): string | undefined {
    const value = primary ?? legacy;
    return value && value.trim().length > 0 ? value : undefined;
  }

  private isUuid(value: string): boolean {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
  }
}
