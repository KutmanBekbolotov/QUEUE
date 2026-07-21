package kg.equeue.backend.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class QrSelfServiceTicketGuardTest extends PostgresIntegrationTest {

    @Autowired
    private TicketService ticketService;

    private CoreData data;

    @BeforeEach
    void seed() {
        data = seedCoreData();
    }

    @Test
    void refusesSecondQrTicketForSamePhoneUntilFirstIsCompleted() {
        TicketDtos.TicketResponse first = createQrTicket("+996 700 000 000");

        assertThat(first.citizenPhone()).isEqualTo("996700000000");
        assertThatThrownBy(() -> createQrTicket("996 (700) 000-000"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("QR_CITIZEN_HAS_UNFINISHED_TICKET");
                    assertThat(ex.getDetails()).containsEntry("ticketId", first.id());
                    assertThat(ex.getDetails()).containsEntry("ticketNumber", first.ticketNumber());
                });

        ticketService.call(first.id(), new TicketDtos.CallTicketRequest(data.windowId()), null);
        ticketService.start(first.id(), null);
        ticketService.complete(first.id(), null);

        TicketDtos.TicketResponse second = createQrTicket("996700000000");

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.ticketNumber()).isEqualTo("A-002");
    }

    @Test
    void rejectsQrTicketWithoutPhone() {
        assertThatThrownBy(() -> createQrTicket("  "))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("QR_CITIZEN_PHONE_REQUIRED");
                });
    }

    @Test
    void concurrentQrTicketsForSamePhoneCreateOnlyOneTicket() throws Exception {
        List<ConcurrentResult<TicketDtos.TicketResponse>> results = runConcurrently(
                8,
                () -> createQrTicket("+996 700 000 000")
        );

        List<TicketDtos.TicketResponse> created = results.stream()
                .filter(ConcurrentResult::success)
                .map(ConcurrentResult::value)
                .toList();
        List<ApiException> conflicts = results.stream()
                .filter(result -> !result.success())
                .map(ConcurrentResult::error)
                .filter(ApiException.class::isInstance)
                .map(ApiException.class::cast)
                .toList();

        assertThat(created).hasSize(1);
        assertThat(conflicts).hasSize(7);
        assertThat(conflicts).allSatisfy(ex -> assertThat(ex.getCode()).isEqualTo("QR_CITIZEN_HAS_UNFINISHED_TICKET"));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM tickets
                WHERE department_id = ?
                  AND source = 'QR_SELF_SERVICE'
                  AND citizen_phone = '996700000000'
                """, Integer.class, data.departmentId())).isEqualTo(1);
    }

    private TicketDtos.TicketResponse createQrTicket(String citizenPhone) {
        return ticketService.createSelfServiceTicket(new TicketDtos.CreateTicketRequest(
                data.departmentId(),
                data.serviceId(),
                null,
                "Citizen",
                null,
                citizenPhone,
                TicketSource.QR_SELF_SERVICE,
                null,
                null,
                null
        ), null);
    }
}
