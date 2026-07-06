package kg.equeue.backend.bookings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.bookings.BookingDtos.BookingResponse;
import kg.equeue.backend.bookings.BookingDtos.CreateBookingRequest;
import kg.equeue.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CreateBookingConcurrencyTest extends PostgresIntegrationTest {

    @Autowired
    private BookingService bookingService;

    private CoreData data;

    @BeforeEach
    void seed() {
        data = seedCoreData();
    }

    @Test
    void concurrentBookingRequestsCannotOverbookOneSlot() throws Exception {
        UUID slotId = UUID.randomUUID();
        LocalDate slotDate = LocalDate.now().plusDays(2);
        insertSlot(slotId, data, slotDate, "09:00", "09:30", 1);

        List<ConcurrentResult<BookingResponse>> results = runConcurrently(8, () -> bookingService.create(
                new CreateBookingRequest(
                        data.departmentId(),
                        data.serviceId(),
                        slotId,
                        "Citizen",
                        "12345678901234",
                        "+996700000000",
                        null,
                        BookingSource.ADMIN_CREATED,
                        null,
                        null
                ),
                null
        ));

        assertThat(results).filteredOn(ConcurrentResult::success).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bookings WHERE slot_id = ?", Integer.class, slotId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT booked_count FROM booking_slots WHERE id = ?", Integer.class, slotId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM booking_slots WHERE id = ?", String.class, slotId))
                .isEqualTo("FULL");
    }
}
