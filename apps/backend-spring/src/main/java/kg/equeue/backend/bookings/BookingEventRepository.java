package kg.equeue.backend.bookings;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingEventRepository extends JpaRepository<BookingEventEntity, UUID> {
}

