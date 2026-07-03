package kg.equeue.backend.bookingslots;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface BookingSlotRepository extends JpaRepository<BookingSlotEntity, UUID> {

    List<BookingSlotEntity> findByDepartmentIdAndServiceIdAndSlotDateOrderBySlotStartAsc(UUID departmentId, UUID serviceId, LocalDate slotDate);

    List<BookingSlotEntity> findByDepartmentIdAndServiceIdAndSlotDateBetweenOrderBySlotDateAscSlotStartAsc(UUID departmentId, UUID serviceId, LocalDate from, LocalDate to);

    boolean existsByDepartmentIdAndServiceIdAndSlotDateAndSlotStart(UUID departmentId, UUID serviceId, LocalDate slotDate, java.time.LocalTime slotStart);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BookingSlotEntity s where s.id = :id")
    Optional<BookingSlotEntity> findWithLockById(UUID id);
}

