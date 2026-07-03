import { HttpService } from '@nestjs/axios';
import { ConfigService } from '@nestjs/config';
import { AxiosResponse } from 'axios';
import { of } from 'rxjs';
import { BackendClientService } from '../src/backend-client/backend-client.service';

describe('BackendClientService', () => {
  it('forwards correlation and idempotency headers to Spring backend', async () => {
    const request = {
      headers: {
        'x-request-id': 'req-1',
        'x-correlation-id': 'corr-1',
        'idempotency-key': 'idem-1',
        'x-external-request-id': 'ext-1',
      },
    } as never;
    const httpService = {
      request: jest.fn().mockReturnValue(of(response({ ok: true }))),
    } as unknown as HttpService;
    const config = {
      get: jest.fn((key: string) => (key === 'BACKEND_INTEGRATION_KEY' ? 'integration-secret' : 'http://backend.test')),
    } as unknown as ConfigService;

    const service = new BackendClientService(httpService, config);
    await expect(service.post('/api/v1/booking', { a: 1 }, request, 'ZENOSS')).resolves.toEqual({ ok: true });

    expect(httpService.request).toHaveBeenCalledWith(
      expect.objectContaining({
        baseURL: 'http://backend.test',
        url: '/api/v1/booking',
        headers: expect.objectContaining({
          'X-Request-Id': 'req-1',
          'X-Correlation-Id': 'corr-1',
          'Idempotency-Key': 'idem-1',
          'X-External-Request-Id': 'ext-1',
          'X-Integration-Client': 'ZENOSS',
          'X-Backend-Integration-Key': 'integration-secret',
        }),
      }),
    );
  });

  function response(data: unknown): AxiosResponse {
    return {
      data,
      status: 200,
      statusText: 'OK',
      headers: {},
      config: { headers: {} },
    } as AxiosResponse;
  }
});
