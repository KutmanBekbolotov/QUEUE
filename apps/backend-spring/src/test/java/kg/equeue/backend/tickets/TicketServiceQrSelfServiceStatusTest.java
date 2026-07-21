package kg.equeue.backend.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import kg.equeue.backend.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TicketServiceQrSelfServiceStatusTest {

    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final TicketService ticketService = new TicketService(
            ticketRepository,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );

    @Test
    void getQrSelfServiceTicketReturnsQrTicketStatus() {
        TicketEntity ticket = ticket(TicketSource.QR_SELF_SERVICE, TicketStatus.WAITING);
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        TicketDtos.TicketResponse response = ticketService.getQrSelfServiceTicket(ticket.getId());

        assertThat(response.id()).isEqualTo(ticket.getId());
        assertThat(response.ticketNumber()).isEqualTo("A-001");
        assertThat(response.source()).isEqualTo(TicketSource.QR_SELF_SERVICE);
        assertThat(response.status()).isEqualTo(TicketStatus.WAITING);
    }

    @Test
    void getQrSelfServiceTicketHidesNonQrTickets() {
        TicketEntity ticket = ticket(TicketSource.TERMINAL, TicketStatus.WAITING);
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

    private TicketEntity ticket(TicketSource source, TicketStatus status) {
        TicketEntity ticket = new TicketEntity();
        ReflectionTestUtils.setField(ticket, "id", UUID.randomUUID());
        ticket.setTicketNumber("A-001");
        ticket.setTicketPrefix("A");
        ticket.setSequenceNumber(1);
        ticket.setWorkDate(LocalDate.now());
        ticket.setDepartmentId(UUID.randomUUID());
        ticket.setCategoryId(UUID.randomUUID());
        ticket.setServiceId(UUID.randomUUID());
        ticket.setCitizenFullName("Citizen");
        ticket.setCitizenPhone("+996700000000");
        ticket.setSource(source);
        ticket.setStatus(status);
        return ticket;
    }
}
