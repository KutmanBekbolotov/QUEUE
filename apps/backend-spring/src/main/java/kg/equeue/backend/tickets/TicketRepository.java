package kg.equeue.backend.tickets;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface TicketRepository extends JpaRepository<TicketEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketEntity t where t.id = :id")
    Optional<TicketEntity> findWithLockById(UUID id);

    List<TicketEntity> findByDepartmentIdOrderByCreatedAtDesc(UUID departmentId);

    List<TicketEntity> findTop20ByDepartmentIdAndStatusInOrderByCalledAtDescCreatedAtDesc(UUID departmentId, Collection<TicketStatus> statuses);

    Optional<TicketEntity> findFirstByServedByUserIdAndStatusInOrderByCalledAtDescCreatedAtDesc(UUID servedByUserId, Collection<TicketStatus> statuses);

    Optional<TicketEntity> findFirstByWindowIdAndStatusInOrderByCalledAtDescCreatedAtDesc(UUID windowId, Collection<TicketStatus> statuses);

    Optional<TicketEntity> findFirstByDepartmentIdAndSourceAndCitizenPhoneAndStatusInOrderByCreatedAtAsc(
            UUID departmentId,
            TicketSource source,
            String citizenPhone,
            Collection<TicketStatus> statuses
    );
}
