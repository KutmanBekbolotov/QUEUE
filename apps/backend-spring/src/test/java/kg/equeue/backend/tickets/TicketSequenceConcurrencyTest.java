package kg.equeue.backend.tickets;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Phase 2: enable with PostgreSQL/Testcontainers after ticket sequence service is implemented.")
class TicketSequenceConcurrencyTest {

    @Test
    void concurrentTicketNumberGenerationMustNotDuplicateNumbers() {
    }
}

