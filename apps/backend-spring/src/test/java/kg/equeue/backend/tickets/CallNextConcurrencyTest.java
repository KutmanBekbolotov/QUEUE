package kg.equeue.backend.tickets;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Phase 2 skeleton: enable with PostgreSQL/Testcontainers to verify FOR UPDATE SKIP LOCKED call-next behavior.")
class CallNextConcurrencyTest {

    @Test
    void concurrentOperatorsMustNotCallSameWaitingTicket() {
    }
}

