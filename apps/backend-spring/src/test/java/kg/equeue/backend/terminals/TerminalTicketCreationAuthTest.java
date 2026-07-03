package kg.equeue.backend.terminals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.common.DeviceTokenService;
import kg.equeue.backend.departmentservices.DepartmentServiceRepository;
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
    private final DeviceTokenService deviceTokenService = new DeviceTokenService();
    private final CapturingTicketService ticketService = new CapturingTicketService();
    private final TerminalService terminalService = new TerminalService(
            terminalRepository,
            departmentServiceRepository,
            deviceTokenService,
            ticketService
    );

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
