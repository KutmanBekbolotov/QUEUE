package kg.equeue.backend.bookings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BookingLifecycleTest {

    @Test
    void bookingEntitySynchronizesPhase3AndLegacyColumnsBeforePersist() {
        UUID slotId = UUID.randomUUID();
        BookingEntity booking = new BookingEntity();
        booking.setBookingNumber("B-1");
        booking.setDepartmentId(UUID.randomUUID());
        booking.setServiceId(UUID.randomUUID());
        booking.setSlotId(slotId);
        booking.setExternalSource(BookingSource.TUNDUK);
        booking.setExternalId("tunduk-1");
        booking.setBookingDate(LocalDate.of(2026, 7, 2));
        booking.setBookingStart(LocalTime.of(9, 0));
        booking.setBookingEnd(LocalTime.of(9, 15));
        booking.setQrToken("qr-token");

        booking.onCreate();

        assertThat(ReflectionTestUtils.getField(booking, "bookingSlotId")).isEqualTo(slotId);
        assertThat(ReflectionTestUtils.getField(booking, "bookedDate")).isEqualTo(booking.getBookingDate());
        assertThat(ReflectionTestUtils.getField(booking, "startsAt")).isEqualTo(booking.getBookingStart());
        assertThat(ReflectionTestUtils.getField(booking, "endsAt")).isEqualTo(booking.getBookingEnd());
        assertThat(ReflectionTestUtils.getField(booking, "source")).isEqualTo("TUNDUK");
        assertThat(ReflectionTestUtils.getField(booking, "externalClientCode")).isEqualTo("TUNDUK");
        assertThat(ReflectionTestUtils.getField(booking, "externalBookingId")).isEqualTo("tunduk-1");
        assertThat(booking.getCreatedAt()).isNotNull();
        assertThat(booking.getUpdatedAt()).isNotNull();
    }
}
