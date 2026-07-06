package kg.equeue.backend.tickets;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import kg.equeue.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TicketSequenceConcurrencyTest extends PostgresIntegrationTest {

    @Autowired
    private TicketService ticketService;

    private CoreData data;

    @BeforeEach
    void seed() {
        data = seedCoreData();
    }

    @Test
    void concurrentTicketNumberGenerationMustNotDuplicateNumbers() throws Exception {
        List<ConcurrentResult<TicketDtos.TicketResponse>> results = runConcurrently(20, () -> ticketService.create(
                new TicketDtos.CreateTicketRequest(
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
                ),
                null
        ));

        assertThat(results).allMatch(ConcurrentResult::success);
        assertThat(results.stream().map(result -> result.value().ticketNumber()).toList())
                .doesNotHaveDuplicates()
                .contains("A-001", "A-020");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT current_value
                FROM ticket_sequences
                WHERE department_id = ? AND service_category_id = ? AND work_date = ?
                """, Integer.class, data.departmentId(), data.categoryId(), LocalDate.now()))
                .isEqualTo(20);
    }
}
