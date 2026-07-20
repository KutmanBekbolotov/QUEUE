package kg.equeue.backend.terminals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.common.DeviceTokenService;
import kg.equeue.backend.departmentservices.DepartmentServiceEntity;
import kg.equeue.backend.departmentservices.DepartmentServiceRepository;
import kg.equeue.backend.servicecategories.ServiceCategoryEntity;
import kg.equeue.backend.servicecategories.ServiceCategoryRepository;
import kg.equeue.backend.services.QueueServiceEntity;
import kg.equeue.backend.services.QueueServiceRepository;
import kg.equeue.backend.ticketevents.TicketActorType;
import kg.equeue.backend.tickets.TicketDtos.CreateTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.TicketResponse;
import kg.equeue.backend.tickets.TicketService;
import kg.equeue.backend.tickets.TicketSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

class TerminalTicketCreationAuthTest {

    private final TerminalRepository terminalRepository = org.mockito.Mockito.mock(TerminalRepository.class);
    private final DepartmentServiceRepository departmentServiceRepository = org.mockito.Mockito.mock(DepartmentServiceRepository.class);
    private final QueueServiceRepository queueServiceRepository = org.mockito.Mockito.mock(QueueServiceRepository.class);
    private final ServiceCategoryRepository serviceCategoryRepository = org.mockito.Mockito.mock(ServiceCategoryRepository.class);
    private final DeviceTokenService deviceTokenService = new DeviceTokenService();
    private final CapturingTicketService ticketService = new CapturingTicketService();
    private final TerminalService terminalService = new TerminalService(
            terminalRepository,
            departmentServiceRepository,
            queueServiceRepository,
            serviceCategoryRepository,
            deviceTokenService,
            ticketService
    );

    @Test
    void configReturnsTerminalEnabledServicesAndCategories() {
        UUID terminalId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        TerminalEntity terminal = terminal(terminalId, departmentId);
        DepartmentServiceEntity departmentService = new DepartmentServiceEntity();
        departmentService.setDepartmentId(departmentId);
        departmentService.setServiceId(serviceId);
        departmentService.setTerminalEnabled(true);
        QueueServiceEntity service = new QueueServiceEntity();
        ReflectionTestUtils.setField(service, "id", serviceId);
        service.setCode("REG");
        service.setName("Регистрация");
        service.setCategoryId(categoryId);
        ServiceCategoryEntity category = new ServiceCategoryEntity();
        ReflectionTestUtils.setField(category, "id", categoryId);
        category.setCode("TS");
        category.setName("Категория");
        category.setTicketPrefix("A");
        when(terminalRepository.findById(terminalId)).thenReturn(Optional.of(terminal));
        when(departmentServiceRepository.findByDepartmentIdAndActiveTrueOrderByServiceIdAsc(departmentId))
                .thenReturn(List.of(departmentService));
        when(queueServiceRepository.findAllById(List.of(serviceId))).thenReturn(List.of(service));
        when(serviceCategoryRepository.findAllById(List.of(categoryId))).thenReturn(List.of(category));
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Device-Token", "raw-token");

        TerminalDtos.TerminalConfigResponse response = terminalService.config(terminalId, httpRequest);

        assertThat(response.terminalId()).isEqualTo(terminalId);
        assertThat(response.departmentId()).isEqualTo(departmentId);
        assertThat(response.serviceIds()).containsExactly(serviceId);
        assertThat(response.services()).singleElement().satisfies(configService -> {
            assertThat(configService.id()).isEqualTo(serviceId);
            assertThat(configService.code()).isEqualTo("REG");
            assertThat(configService.categoryId()).isEqualTo(categoryId);
            assertThat(configService.categoryCode()).isEqualTo("TS");
            assertThat(configService.type()).isEqualTo("VS");
            assertThat(configService.name().ru()).isEqualTo("Регистрация");
            assertThat(configService.name().ky()).isEqualTo("Регистрация");
        });
        assertThat(response.categories()).singleElement().satisfies(configCategory -> {
            assertThat(configCategory.id()).isEqualTo(categoryId);
            assertThat(configCategory.code()).isEqualTo("TS");
            assertThat(configCategory.type()).isEqualTo("VS");
            assertThat(configCategory.name().ru()).isEqualTo("Категория");
            assertThat(configCategory.name().ky()).isEqualTo("Категория");
        });
    }

    @Test
    void terminalMustCreateTicketsOnlyForConfiguredDepartmentWithValidToken() {
        UUID terminalId = UUID.randomUUID();
        UUID terminalDepartmentId = UUID.randomUUID();
        TerminalEntity terminal = terminal(terminalId, terminalDepartmentId);
        when(terminalRepository.findById(terminalId)).thenReturn(Optional.of(terminal));
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Device-Token", "raw-token");

        TerminalDtos.TerminalCreateTicketRequest request = new TerminalDtos.TerminalCreateTicketRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Citizen",
                "123",
                "+996700000000",
                null
        );

        assertThatThrownBy(() -> terminalService.createTicket(terminalId, request, httpRequest))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("TERMINAL_DEPARTMENT_DENIED"));
        assertThat(ticketService.calls).isZero();
    }

    @Test
    void terminalCreatesDeviceTicketForOwnDepartmentWithTerminalSource() {
        UUID terminalId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        TerminalEntity terminal = terminal(terminalId, departmentId);
        when(terminalRepository.findById(terminalId)).thenReturn(Optional.of(terminal));
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Device-Token", "raw-token");

        TerminalDtos.TerminalCreateTicketRequest request = new TerminalDtos.TerminalCreateTicketRequest(
                departmentId,
                serviceId,
                "Citizen",
                "123",
                "+996700000000",
                "comment"
        );

        terminalService.createTicket(terminalId, request, httpRequest);

        assertThat(terminal.getLastSeenAt()).isNotNull();
        assertThat(ticketService.calls).isEqualTo(1);
        assertThat(ticketService.request.departmentId()).isEqualTo(departmentId);
        assertThat(ticketService.request.serviceId()).isEqualTo(serviceId);
        assertThat(ticketService.request.source()).isEqualTo(TicketSource.TERMINAL);
        assertThat(ticketService.request.citizenPin()).isEqualTo("123");
        assertThat(ticketService.actorType).isEqualTo(TicketActorType.DEVICE);
        assertThat(ticketService.actorId).isEqualTo(terminalId);
    }

    private TerminalEntity terminal(UUID terminalId, UUID departmentId) {
        TerminalEntity terminal = new TerminalEntity();
        ReflectionTestUtils.setField(terminal, "id", terminalId);
        ReflectionTestUtils.setField(terminal, "departmentId", departmentId);
        ReflectionTestUtils.setField(terminal, "code", "T-1");
        ReflectionTestUtils.setField(terminal, "name", "Terminal 1");
        ReflectionTestUtils.setField(terminal, "tokenHash", deviceTokenService.hash("raw-token"));
        ReflectionTestUtils.setField(terminal, "active", true);
        return terminal;
    }

    static class CapturingTicketService extends TicketService {
        int calls;
        CreateTicketRequest request;
        TicketActorType actorType;
        UUID actorId;

        CapturingTicketService() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public TicketResponse createDeviceTicket(CreateTicketRequest request,
                                                 TicketActorType actorType,
                                                 UUID actorId,
                                                 HttpServletRequest httpRequest) {
            this.calls++;
            this.request = request;
            this.actorType = actorType;
            this.actorId = actorId;
            return null;
        }
    }
}
