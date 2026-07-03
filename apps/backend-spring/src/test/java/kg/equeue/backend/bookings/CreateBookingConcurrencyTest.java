package kg.equeue.backend.bookings;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Phase 3 skeleton: enable with PostgreSQL/Testcontainers to verify SELECT FOR UPDATE prevents overbooking.")
class CreateBookingConcurrencyTest {

    @Test
    void concurrentBookingRequestsCannotOverbookOneSlot() {
    }
}

