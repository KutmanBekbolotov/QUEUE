package kg.equeue.backend.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.services.QueueServiceEntity;
import kg.equeue.backend.services.QueueServiceRepository;
import kg.equeue.backend.servicewindows.ServiceWindowEntity;
import kg.equeue.backend.servicewindows.ServiceWindowRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TicketServiceQrSelfServiceStatusTest {

    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final QueueServiceRepository queueServiceRepository = mock(QueueServiceRepository.class);
    private final ServiceWindowRepository serviceWindowRepository = mock(ServiceWindowRepository.class);
    private final TicketService ticketService = new TicketService(
            ticketRepository,
            null,
            null,
            null,
            null,
            queueServiceRepository,
            null,
            null,
            serviceWindowRepository,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );

    @Test
    void getQrSelfServiceTicketReturnsQrTicketStatusAndDisplayFields() {
        UUID serviceId = UUID.randomUUID();
        UUID windowId = UUID.randomUUID();
        TicketEntity ticket = ticket(TicketSource.QR_SELF_SERVICE, TicketStatus.CALLED, serviceId, windowId);
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(queueServiceRepository.findById(serviceId)).thenReturn(Optional.of(service(serviceId, "Vehicle registration")));
        when(serviceWindowRepository.findById(windowId)).thenReturn(Optional.of(window(windowId, "5", "Window 5")));

        TicketDtos.TicketResponse response = ticketService.getQrSelfServiceTicket(ticket.getId());

        assertThat(response.id()).isEqualTo(ticket.getId());
        assertThat(response.ticketNumber()).isEqualTo("A-001");
        assertThat(response.source()).isEqualTo(TicketSource.QR_SELF_SERVICE);
        assertThat(response.status()).isEqualTo(TicketStatus.CALLED);
        assertThat(response.windowId()).isEqualTo(windowId);
        assertThat(response.serviceWindowId()).isEqualTo(windowId);
        assertThat(response.windowNumber()).isEqualTo("Window 5");
        assertThat(response.serviceName()).isNotNull();
        assertThat(response.serviceName().ru()).isEqualTo("Vehicle registration");
    }

    @Test
    void getQrSelfServiceTicketHidesNonQrTickets() {
        TicketEntity ticket = ticket(TicketSource.TERMINAL, TicketStatus.WAITING, UUID.randomUUID(), null);
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.getQrSelfServiceTicket(ticket.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(404);
                    assertThat(ex.getCode()).isEqualTo("QR_TICKET_NOT_FOUND");
                });
    }

    @Test
    void getQrSelfServiceTicketReturnsNotFoundWhenMissing() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getQrSelfServiceTicket(ticketId))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(404);
                    assertThat(ex.getCode()).isEqualTo("QR_TICKET_NOT_FOUND");
                });
    }

    private TicketEntity ticket(TicketSource source, TicketStatus status, UUID serviceId, UUID windowId) {
        TicketEntity ticket = new TicketEntity();
        ReflectionTestUtils.setField(ticket, "id", UUID.randomUUID());
        ticket.setTicketNumber("A-001");
        ticket.setTicketPrefix("A");
        ticket.setSequenceNumber(1);
        ticket.setWorkDate(LocalDate.now());
        ticket.setDepartmentId(UUID.randomUUID());
        ticket.setCategoryId(UUID.randomUUID());
        ticket.setServiceId(serviceId);
        ticket.setWindowId(windowId);
        ticket.setCitizenFullName("Citizen");
        ticket.setCitizenPhone("+996700000000");
        ticket.setSource(source);
        ticket.setStatus(status);
        return ticket;
    }

    private QueueServiceEntity service(UUID serviceId, String name) {
        QueueServiceEntity service = new QueueServiceEntity();
        ReflectionTestUtils.setField(service, "id", serviceId);
        service.setCategoryId(UUID.randomUUID());
        service.setCode("SERVICE");
        service.setName(name);
        return service;
    }

    private ServiceWindowEntity window(UUID windowId, String code, String displayName) {
        ServiceWindowEntity window = new ServiceWindowEntity();
        ReflectionTestUtils.setField(window, "id", windowId);
        window.setDepartmentId(UUID.randomUUID());
        window.setHallId(UUID.randomUUID());
        window.setCode(code);
        window.setDisplayName(displayName);
        return window;
    }
}
