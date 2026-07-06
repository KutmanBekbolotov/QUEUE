package kg.equeue.backend.bookings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import kg.equeue.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SlotGenerationRulesTest extends PostgresIntegrationTest {

    @Autowired
    private BookingService bookingService;

    private CoreData data;

    @BeforeEach
    void seed() {
        data = seedCoreData();
    }

    @Test
    void slotGenerationRespectsWorkingHoursAndBreakTime() {
        LocalDate date = LocalDate.now().plusDays(3);
        jdbcTemplate.update("""
                INSERT INTO department_working_hours (department_id, day_of_week, opens_at, closes_at, break_starts_at, break_ends_at, active)
                VALUES (?, ?, '09:00', '12:00', '10:00', '10:30', true)
                """, data.departmentId(), date.getDayOfWeek().getValue());

        BookingDtos.GenerateSlotsResponse response = bookingService.generateSlots(
                new BookingDtos.GenerateSlotsRequest(data.departmentId(), data.serviceId(), date, date, 30, 2, false),
                null
        );

        assertThat(response.created()).isEqualTo(5);
        assertThat(response.skipped()).isZero();
        assertThat(jdbcTemplate.queryForList("""
                SELECT slot_start::text
                FROM booking_slots
                WHERE department_id = ? AND service_id = ? AND slot_date = ?
                ORDER BY slot_start
                """, String.class, data.departmentId(), data.serviceId(), date))
                .containsExactly("09:00:00", "09:30:00", "10:30:00", "11:00:00", "11:30:00");
    }
}
