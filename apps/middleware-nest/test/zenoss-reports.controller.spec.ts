import { BackendClientService } from '../src/backend-client/backend-client.service';
import { ZenossIntegrationController } from '../src/zenoss-integration/zenoss-integration.controller';

describe('Zenoss reports proxy', () => {
  it('forwards summary report query to Spring backend', async () => {
    const backendClient = {
      get: jest.fn().mockResolvedValue({ totalTickets: 1 }),
    } as unknown as BackendClientService;
    const controller = new ZenossIntegrationController(backendClient);
    const request = { headers: { 'x-request-id': 'req-1' } } as never;
    const query = { dateFrom: '2026-07-01', dateTo: '2026-07-31' };

    await expect(controller.reportSummary(query, request)).resolves.toEqual({ totalTickets: 1 });

    expect(backendClient.get).toHaveBeenCalledWith(
      '/api/v1/reports/summary',
      request,
      'ZENOSS',
      query,
    );
  });

  it('forwards export creation without requiring idempotency interceptor', async () => {
    const backendClient = {
      post: jest.fn().mockResolvedValue({ id: 'export-id', status: 'COMPLETED' }),
    } as unknown as BackendClientService;
    const controller = new ZenossIntegrationController(backendClient);
    const request = { headers: { 'x-request-id': 'req-1' } } as never;
    const body = {
      reportType: 'BY_SERVICE',
      format: 'CSV',
      filters: { dateFrom: '2026-07-01', dateTo: '2026-07-31' },
    };

    await expect(controller.reportExport(body, request)).resolves.toEqual({ id: 'export-id', status: 'COMPLETED' });

    expect(backendClient.post).toHaveBeenCalledWith('/api/v1/reports/export', body, request, 'ZENOSS');
  });
});
