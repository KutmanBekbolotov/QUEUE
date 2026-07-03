package kg.equeue.backend.bookings;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {

    Optional<BookingEntity> findByQrToken(String qrToken);

    Optional<BookingEntity> findByExternalSourceAndExternalId(BookingSource externalSource, String externalId);

    Optional<BookingEntity> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BookingEntity b where b.id = :id")
    Optional<BookingEntity> findWithLockById(UUID id);

    List<BookingEntity> findTop100ByStatusAndBookingDateLessThanEqualOrderByBookingDateAscBookingStartAsc(BookingStatus status, LocalDate bookingDate);

    List<BookingEntity> findByStatusAndBookingDateAndBookingStartLessThanEqual(BookingStatus status, LocalDate bookingDate, LocalTime bookingStart);
}

