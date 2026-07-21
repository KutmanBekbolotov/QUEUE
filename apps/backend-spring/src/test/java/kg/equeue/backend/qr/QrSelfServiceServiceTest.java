package kg.equeue.backend.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kg.equeue.backend.departmentservices.DepartmentServiceEntity;
import kg.equeue.backend.departmentservices.DepartmentServiceRepository;
import kg.equeue.backend.departments.DepartmentEntity;
import kg.equeue.backend.departments.DepartmentRepository;
import kg.equeue.backend.servicecategories.ServiceCategoryEntity;
import kg.equeue.backend.servicecategories.ServiceCategoryRepository;
import kg.equeue.backend.services.QueueServiceEntity;
import kg.equeue.backend.services.QueueServiceRepository;
import kg.equeue.backend.tickets.TicketDtos.CreateTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.TicketResponse;
import kg.equeue.backend.tickets.TicketService;
import kg.equeue.backend.tickets.TicketSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

class QrSelfServiceServiceTest {

    private final DepartmentRepository departmentRepository = org.mockito.Mockito.mock(DepartmentRepository.class);
    private final DepartmentServiceRepository departmentServiceRepository = org.mockito.Mockito.mock(DepartmentServiceRepository.class);
    private final QueueServiceRepository queueServiceRepository = org.mockito.Mockito.mock(QueueServiceRepository.class);
    private final ServiceCategoryRepository serviceCategoryRepository = org.mockito.Mockito.mock(ServiceCategoryRepository.class);
    private final CapturingTicketService ticketService = new CapturingTicketService();
    private final QrSelfServiceService qrSelfServiceService = new QrSelfServiceService(
            departmentRepository,
            departmentServiceRepository,
            queueServiceRepository,
            serviceCategoryRepository,
            ticketService
    );

    @Test
    void configReturnsOnlyQrEnabledActiveServicesWithCategoryCodes() {
        UUID departmentId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID hiddenServiceId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        DepartmentEntity department = department(departmentId);
        DepartmentServiceEntity qrEnabled = departmentService(departmentId, serviceId, true);
        DepartmentServiceEntity qrDisabled = departmentService(departmentId, hiddenServiceId, false);
        QueueServiceEntity service = service(serviceId, categoryId, "TS_PRIMARY_REGISTRATION");
        ServiceCategoryEntity category = category(categoryId, "TS");
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(departmentServiceRepository.findByDepartmentIdAndActiveTrueOrderByServiceIdAsc(departmentId))
                .thenReturn(List.of(qrEnabled, qrDisabled));
        when(queueServiceRepository.findAllById(List.of(serviceId))).thenReturn(List.of(service));
        when(serviceCategoryRepository.findAllById(List.of(categoryId))).thenReturn(List.of(category));

        QrDtos.QrConfigResponse response = qrSelfServiceService.config(departmentId);

        assertThat(response.departmentId()).isEqualTo(departmentId);
        assertThat(response.departmentCode()).isEqualTo("D-1");
        assertThat(response.services()).singleElement().satisfies(configService -> {
            assertThat(configService.id()).isEqualTo(serviceId);
            assertThat(configService.code()).isEqualTo("TS_PRIMARY_REGISTRATION");
            assertThat(configService.categoryCode()).isEqualTo("TS");
        });
        assertThat(response.categories()).singleElement().satisfies(configCategory -> {
            assertThat(configCategory.id()).isEqualTo(categoryId);
            assertThat(configCategory.code()).isEqualTo("TS");
        });
    }

    @Test
    void createTicketUsesQrSelfServiceSource() {
        UUID departmentId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        QrDtos.QrCreateTicketRequest request = new QrDtos.QrCreateTicketRequest(
                departmentId,
                serviceId,
                "Citizen",
                "+996700000000",
                "from qr"
        );

        qrSelfServiceService.createTicket(request, new MockHttpServletRequest());

        assertThat(ticketService.calls).isEqualTo(1);
        assertThat(ticketService.request.departmentId()).isEqualTo(departmentId);
        assertThat(ticketService.request.serviceId()).isEqualTo(serviceId);
        assertThat(ticketService.request.source()).isEqualTo(TicketSource.QR_SELF_SERVICE);
        assertThat(ticketService.request.citizenFullName()).isEqualTo("Citizen");
        assertThat(ticketService.request.citizenPin()).isNull();
        assertThat(ticketService.request.comment()).isEqualTo("from qr");
    }

    @Test
    void ticketReadsQrSelfServiceTicketById() {
        UUID ticketId = UUID.randomUUID();

        qrSelfServiceService.ticket(ticketId);

        assertThat(ticketService.readCalls).isEqualTo(1);
        assertThat(ticketService.ticketId).isEqualTo(ticketId);
    }

    private DepartmentEntity department(UUID departmentId) {
        DepartmentEntity department = new DepartmentEntity();
        ReflectionTestUtils.setField(department, "id", departmentId);
        department.setCode("D-1");
        department.setName("Department");
        department.setActive(true);
        department.setClosed(false);
        return department;
    }

    private DepartmentServiceEntity departmentService(UUID departmentId, UUID serviceId, boolean qrEnabled) {
        DepartmentServiceEntity departmentService = new DepartmentServiceEntity();
        departmentService.setDepartmentId(departmentId);
        departmentService.setServiceId(serviceId);
        departmentService.setQrEnabled(qrEnabled);
        return departmentService;
    }

    private QueueServiceEntity service(UUID serviceId, UUID categoryId, String code) {
        QueueServiceEntity service = new QueueServiceEntity();
        ReflectionTestUtils.setField(service, "id", serviceId);
        service.setCategoryId(categoryId);
        service.setCode(code);
        service.setName("Service");
        service.setActive(true);
        return service;
    }

    private ServiceCategoryEntity category(UUID categoryId, String code) {
        ServiceCategoryEntity category = new ServiceCategoryEntity();
        ReflectionTestUtils.setField(category, "id", categoryId);
        category.setCode(code);
        category.setName("Category");
        category.setTicketPrefix("A");
        category.setActive(true);
        return category;
    }

    static class CapturingTicketService extends TicketService {
        int calls;
        int readCalls;
        CreateTicketRequest request;
        UUID ticketId;

        CapturingTicketService() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public TicketResponse createSelfServiceTicket(CreateTicketRequest request, HttpServletRequest httpRequest) {
            this.calls++;
            this.request = request;
            return null;
        }

        @Override
        public TicketResponse getQrSelfServiceTicket(UUID ticketId) {
            this.readCalls++;
            this.ticketId = ticketId;
            return null;
        }
    }
}
