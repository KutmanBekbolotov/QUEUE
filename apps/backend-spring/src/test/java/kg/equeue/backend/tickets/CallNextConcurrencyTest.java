package kg.equeue.backend.tickets;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kg.equeue.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CallNextConcurrencyTest extends PostgresIntegrationTest {

    @Autowired
    private TicketService ticketService;

    private CoreData data;

    @BeforeEach
    void seed() {
        data = seedCoreData();
        for (int i = 0; i < 5; i++) {
            ticketService.create(new TicketDtos.CreateTicketRequest(
                    data.departmentId(),
                    data.serviceId(),
                    null,
                    null,
                    null,
                    null,
                    TicketSource.ADMIN_CREATED,
                    null,
                    null,
                    null
            ), null);
        }
    }

    @Test
    void concurrentOperatorsMustNotCallSameWaitingTicket() throws Exception {
        List<ConcurrentResult<TicketDtos.TicketResponse>> results = runConcurrently(10, () -> ticketService.callNext(
                new TicketDtos.CallNextTicketRequest(data.departmentId(), data.windowId(), List.of(data.serviceId())),
                null
        ));

        List<TicketDtos.TicketResponse> called = results.stream()
                .filter(ConcurrentResult::success)
                .map(ConcurrentResult::value)
                .toList();
        assertThat(called).hasSize(5);
        assertThat(called.stream().map(TicketDtos.TicketResponse::id).toList()).doesNotHaveDuplicates();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM tickets
                WHERE department_id = ? AND status = 'CALLED'
                """, Integer.class, data.departmentId()))
                .isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM tickets
                WHERE department_id = ? AND status = 'WAITING'
                """, Integer.class, data.departmentId()))
                .isZero();
    }
}
