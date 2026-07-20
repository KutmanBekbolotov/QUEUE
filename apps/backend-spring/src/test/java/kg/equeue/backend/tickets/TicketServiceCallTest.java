package kg.equeue.backend.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.auth.AuthenticatedPrincipal;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.servicewindows.ServiceWindowEntity;
import kg.equeue.backend.servicewindows.ServiceWindowRepository;
import kg.equeue.backend.servicewindows.WindowStatus;
import kg.equeue.backend.ticketevents.TicketEventEntity;
import kg.equeue.backend.ticketevents.TicketEventRepository;
import kg.equeue.backend.users.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

class TicketServiceCallTest {

    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final TicketEventRepository ticketEventRepository = mock(TicketEventRepository.class);
    private final TicketDomainEventPublisher ticketDomainEventPublisher = new NoopTicketDomainEventPublisher();
    private final ServiceWindowRepository serviceWindowRepository = mock(ServiceWindowRepository.class);
    private final AuditService auditService = new NoopAuditService();
    private final TicketService ticketService = new TicketService(
            ticketRepository,
            ticketEventRepository,
            null,
            ticketDomainEventPublisher,
            null,
            null,
            null,
            null,
            serviceWindowRepository,
            null,
            null,
            null,
            auditService,
            null,
            new ObjectMapper(),
            null
    );

    @BeforeEach
    void authenticateAsSuperAdmin() {
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                UUID.randomUUID(),
                "admin",
                1,
                UserStatus.ACTIVE,
                authorities
        );
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void callAllowsRecallForAlreadyCalledTicketInSameWindow() {
        UUID departmentId = UUID.randomUUID();
        UUID windowId = UUID.randomUUID();
        Instant previousCalledAt = Instant.parse("2026-07-20T06:00:00Z");
        TicketEntity ticket = ticket(departmentId, TicketStatus.CALLED);
        ticket.setWindowId(windowId);
        ticket.setCalledAt(previousCalledAt);
        ServiceWindowEntity window = openWindow(windowId, departmentId);
        when(ticketRepository.findWithLockById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(serviceWindowRepository.findById(windowId)).thenReturn(Optional.of(window));
        when(ticketRepository.save(any(TicketEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketDtos.TicketResponse response = ticketService.call(
                ticket.getId(),
                new TicketDtos.CallTicketRequest(windowId),
                new MockHttpServletRequest()
        );

        assertThat(response.status()).isEqualTo(TicketStatus.CALLED);
        assertThat(response.windowId()).isEqualTo(windowId);
        assertThat(response.calledAt()).isAfter(previousCalledAt);
        ArgumentCaptor<TicketEventEntity> eventCaptor = ArgumentCaptor.forClass(TicketEventEntity.class);
        verify(ticketEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("ticket.called");
        assertThat(eventCaptor.getValue().getFromStatus()).isEqualTo(TicketStatus.CALLED);
        assertThat(eventCaptor.getValue().getToStatus()).isEqualTo(TicketStatus.CALLED);
    }

    @Test
    void recallDoesNotMoveAlreadyCalledTicketToAnotherWindow() {
        UUID departmentId = UUID.randomUUID();
        UUID originalWindowId = UUID.randomUUID();
        UUID requestedWindowId = UUID.randomUUID();
        TicketEntity ticket = ticket(departmentId, TicketStatus.CALLED);
        ticket.setWindowId(originalWindowId);
        ServiceWindowEntity requestedWindow = openWindow(requestedWindowId, departmentId);
        when(ticketRepository.findWithLockById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(serviceWindowRepository.findById(requestedWindowId)).thenReturn(Optional.of(requestedWindow));

        assertThatThrownBy(() -> ticketService.call(
                ticket.getId(),
                new TicketDtos.CallTicketRequest(requestedWindowId),
                new MockHttpServletRequest()
        )).isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("TICKET_ALREADY_CALLED_TO_ANOTHER_WINDOW"));
        verify(ticketRepository, never()).save(any(TicketEntity.class));
    }

    private TicketEntity ticket(UUID departmentId, TicketStatus status) {
        UUID ticketId = UUID.randomUUID();
        TicketEntity ticket = new TicketEntity();
        ReflectionTestUtils.setField(ticket, "id", ticketId);
        ticket.setTicketNumber("A-001");
        ticket.setTicketPrefix("A");
        ticket.setSequenceNumber(1);
        ticket.setWorkDate(LocalDate.of(2026, 7, 20));
        ticket.setDepartmentId(departmentId);
        ticket.setCategoryId(UUID.randomUUID());
        ticket.setServiceId(UUID.randomUUID());
        ticket.setSource(TicketSource.ADMIN_CREATED);
        ticket.setStatus(status);
        return ticket;
    }

    private ServiceWindowEntity openWindow(UUID windowId, UUID departmentId) {
        ServiceWindowEntity window = new ServiceWindowEntity();
        ReflectionTestUtils.setField(window, "id", windowId);
        window.setDepartmentId(departmentId);
        window.setHallId(UUID.randomUUID());
        window.setCode("WINDOW");
        window.setDisplayName("Window");
        window.setStatus(WindowStatus.OPEN);
        return window;
    }

    static class NoopTicketDomainEventPublisher extends TicketDomainEventPublisher {
        NoopTicketDomainEventPublisher() {
            super(null, null);
        }

        @Override
        public TicketDomainEventPublisher.TicketDomainEvent publish(String eventType, TicketEntity ticket) {
            return null;
        }
    }

    static class NoopAuditService extends AuditService {
        NoopAuditService() {
            super(null);
        }

        @Override
        public void write(String action, String entityType, UUID entityId, String newValue, HttpServletRequest request) {
        }
    }
}
