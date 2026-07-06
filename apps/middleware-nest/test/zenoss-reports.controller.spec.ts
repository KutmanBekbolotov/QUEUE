import { CrmIntegrationService } from '../src/crm-integration/crm-integration.service';
import { ZenossIntegrationController } from '../src/zenoss-integration/zenoss-integration.controller';

describe('Zenoss compatibility aliases', () => {
  it('delegates ticket creation to CRM integration with Zenoss client code', async () => {
    const crmIntegrationService = {
      createTicket: jest.fn().mockResolvedValue({ id: 'ticket-id' }),
    } as unknown as CrmIntegrationService;
    const controller = new ZenossIntegrationController(crmIntegrationService);
    const request = { headers: { 'x-request-id': 'req-1' } } as never;
    const body = {
      departmentId: '0d259eb7-2505-4aa1-9d4d-fc8f418f6111',
      serviceId: '28cb3a2a-915e-44e0-bc7e-61e5511c8f3f',
    };

    await expect(controller.createTicket(body, request)).resolves.toEqual({ id: 'ticket-id' });

    expect(crmIntegrationService.createTicket).toHaveBeenCalledWith(body, request, 'ZENOSS');
  });

  it('delegates directory listing to CRM integration with Zenoss client code', async () => {
    const crmIntegrationService = {
      departments: jest.fn().mockResolvedValue([{ id: 'department-id' }]),
    } as unknown as CrmIntegrationService;
    const controller = new ZenossIntegrationController(crmIntegrationService);
    const request = { headers: { 'x-request-id': 'req-1' } } as never;

    await expect(controller.departments(request)).resolves.toEqual([{ id: 'department-id' }]);

    expect(crmIntegrationService.departments).toHaveBeenCalledWith(request, 'ZENOSS');
  });
});
