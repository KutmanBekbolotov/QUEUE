import 'reflect-metadata';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { CreateExternalBookingDto, ReportExportRequestDto, ReportFilterQueryDto } from '../src/common/external.dto';

describe('external request validation', () => {
  it('accepts a normalized booking request shape', async () => {
    const dto = plainToInstance(CreateExternalBookingDto, {
      departmentId: '0d259eb7-2505-4aa1-9d4d-fc8f418f6111',
      serviceId: '28cb3a2a-915e-44e0-bc7e-61e5511c8f3f',
      slotId: '05bb3c61-bccc-4220-898d-ee7cf57433a0',
      externalBookingId: 'cabinet-123',
    });

    await expect(validate(dto)).resolves.toHaveLength(0);
  });

  it('rejects invalid ids and time format', async () => {
    const dto = plainToInstance(CreateExternalBookingDto, {
      departmentId: 'bad',
      serviceId: 'also-bad',
      slotId: 'not-a-slot-id',
      externalBookingId: 'cabinet-123',
    });

    const errors = await validate(dto);

    expect(errors.map((error) => error.property)).toEqual(
      expect.arrayContaining(['departmentId', 'serviceId', 'slotId']),
    );
  });

  it('accepts a report filter query shape', async () => {
    const dto = plainToInstance(ReportFilterQueryDto, {
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
      departmentId: '0d259eb7-2505-4aa1-9d4d-fc8f418f6111',
      size: '100',
    });

    await expect(validate(dto)).resolves.toHaveLength(0);
    expect(dto.size).toBe(100);
  });

  it('validates report export request shape', async () => {
    const dto = plainToInstance(ReportExportRequestDto, {
      reportType: 'BY_DEPARTMENT',
      format: 'CSV',
      filters: {
        dateFrom: '2026-07-01',
        dateTo: '2026-07-31',
      },
    });

    await expect(validate(dto)).resolves.toHaveLength(0);
  });
});
