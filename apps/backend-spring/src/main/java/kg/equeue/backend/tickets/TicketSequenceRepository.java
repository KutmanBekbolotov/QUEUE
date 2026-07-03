package kg.equeue.backend.tickets;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface TicketSequenceRepository extends JpaRepository<TicketSequenceEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from TicketSequenceEntity s
            where s.departmentId = :departmentId
              and s.serviceCategoryId = :serviceCategoryId
              and s.workDate = :workDate
            """)
    Optional<TicketSequenceEntity> findForUpdate(UUID departmentId, UUID serviceCategoryId, LocalDate workDate);
}

