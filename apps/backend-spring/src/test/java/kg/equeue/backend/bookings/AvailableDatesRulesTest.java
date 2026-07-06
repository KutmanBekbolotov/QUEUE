package kg.equeue.backend.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AvailableDatesRulesTest extends PostgresIntegrationTest {

    @Autowired
    private BookingService bookingService;

    private CoreData data;

    @BeforeEach
    void seed() {
        data = seedCoreData();
    }

    @Test
    void availableDatesExcludeHolidaysAndClosedDepartments() {
        LocalDate holiday = LocalDate.now().plusDays(2);
        LocalDate available = holiday.plusDays(1);
        insertSlot(UUID.randomUUID(), data, holiday, "09:00", "09:30", 1);
        insertSlot(UUID.randomUUID(), data, available, "09:00", "09:30", 1);
        jdbcTemplate.update("""
                INSERT INTO department_holidays (department_id, holiday_date, reason)
                VALUES (?, ?, 'Closed for test')
                """, data.departmentId(), holiday);

        BookingDtos.AvailableDatesResponse response = bookingService.availableDates(
                data.departmentId(),
                data.serviceId(),
                holiday,
                available,
                BookingSource.WEBSITE_CABINET
        );

        assertThat(response.dates()).containsExactly(available);

        jdbcTemplate.update("UPDATE departments SET closed = true WHERE id = ?", data.departmentId());
        assertThatThrownBy(() -> bookingService.availableDates(
                data.departmentId(),
                data.serviceId(),
                holiday,
                available,
                BookingSource.WEBSITE_CABINET
        ))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("DEPARTMENT_NOT_AVAILABLE"));
    }
}
