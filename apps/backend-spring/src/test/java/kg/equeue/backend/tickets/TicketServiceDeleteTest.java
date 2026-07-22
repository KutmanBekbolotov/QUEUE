package kg.equeue.backend.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.common.DepartmentScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

class TicketServiceDeleteTest {

    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final CapturingTicketDomainEventPublisher eventPublisher = new CapturingTicketDomainEventPublisher();
    private final CapturingAuditService auditService = new CapturingAuditService();
    private final TicketService ticketService = new TicketService(
            ticketRepository,
            null,
            null,
            eventPublisher,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new NoopDepartmentScopeService(),
            auditService,
            null,
            null,
            null
    );

    @Test
    void deleteHardDeletesTicketAndPublishesEvent() {
        TicketEntity ticket = ticket(TicketStatus.COMPLETED);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(ticketRepository.findWithLockById(ticket.getId())).thenReturn(Optional.of(ticket));

        ticketService.delete(ticket.getId(), request);

        verify(ticketRepository).delete(ticket);
        verify(ticketRepository).flush();
        assertThat(eventPublisher.eventType).isEqualTo("ticket.deleted");
        assertThat(eventPublisher.ticketId).isEqualTo(ticket.getId());
        assertThat(auditService.action).isEqualTo("TICKET_DELETE");
        assertThat(auditService.entityId).isEqualTo(ticket.getId());
        assertThat(auditService.newValue).contains("A-001");
    }

    @Test
    void deleteReturnsNotFoundWhenTicketMissing() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findWithLockById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.delete(ticketId, new MockHttpServletRequest()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(404);
                    assertThat(ex.getCode()).isEqualTo("TICKET_NOT_FOUND");
                });
    }

    private TicketEntity ticket(TicketStatus status) {
        TicketEntity ticket = new TicketEntity();
        ReflectionTestUtils.setField(ticket, "id", UUID.randomUUID());
        ticket.setTicketNumber("A-001");
        ticket.setTicketPrefix("A");
        ticket.setSequenceNumber(1);
        ticket.setWorkDate(LocalDate.now());
        ticket.setDepartmentId(UUID.randomUUID());
        ticket.setCategoryId(UUID.randomUUID());
        ticket.setServiceId(UUID.randomUUID());
        ticket.setSource(TicketSource.ADMIN_CREATED);
        ticket.setStatus(status);
        return ticket;
    }

    static class CapturingTicketDomainEventPublisher extends TicketDomainEventPublisher {
        String eventType;
        UUID ticketId;

        CapturingTicketDomainEventPublisher() {
            super(null, null);
        }

        @Override
        public TicketDomainEvent publish(String eventType, TicketEntity ticket) {
            this.eventType = eventType;
            this.ticketId = ticket.getId();
            return null;
        }
    }

    static class CapturingAuditService extends AuditService {
        String action;
        UUID entityId;
        String newValue;

        CapturingAuditService() {
            super(null);
        }

        @Override
        public void write(String action, String entityType, UUID entityId, String newValue, HttpServletRequest request) {
            this.action = action;
            this.entityId = entityId;
            this.newValue = newValue;
        }
    }

    static class NoopDepartmentScopeService extends DepartmentScopeService {
        NoopDepartmentScopeService() {
            super(null);
        }

        @Override
        public void requireDepartmentAccess(UUID departmentId) {
        }
    }
}
