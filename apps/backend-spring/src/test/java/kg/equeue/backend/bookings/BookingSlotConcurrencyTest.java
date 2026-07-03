package kg.equeue.backend.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kg.equeue.backend.bookingslots.BookingSlotEntity;
import kg.equeue.backend.bookingslots.BookingSlotStatus;
import org.junit.jupiter.api.Test;

class BookingSlotConcurrencyTest {

    @Test
    void slotCounterCannotExceedCapacity() {
        BookingSlotEntity slot = new BookingSlotEntity();
        slot.setCapacity(2);

        assertThat(slot.hasCapacity()).isTrue();
        slot.incrementBookedCount();
        assertThat(slot.getBookedCount()).isEqualTo(1);
        assertThat(slot.getStatus()).isEqualTo(BookingSlotStatus.ACTIVE);

        slot.incrementBookedCount();
        assertThat(slot.getBookedCount()).isEqualTo(2);
        assertThat(slot.getStatus()).isEqualTo(BookingSlotStatus.FULL);
        assertThat(slot.hasCapacity()).isFalse();

        assertThatThrownBy(slot::incrementBookedCount)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no remaining capacity");
        assertThat(slot.getBookedCount()).isEqualTo(2);
    }

    @Test
    void decrementReopensFullSlotWhenCapacityBecomesAvailable() {
        BookingSlotEntity slot = new BookingSlotEntity();
        slot.setCapacity(1);
        slot.incrementBookedCount();

        slot.decrementBookedCount();

        assertThat(slot.getBookedCount()).isZero();
        assertThat(slot.getStatus()).isEqualTo(BookingSlotStatus.ACTIVE);
        assertThat(slot.hasCapacity()).isTrue();
    }
}
