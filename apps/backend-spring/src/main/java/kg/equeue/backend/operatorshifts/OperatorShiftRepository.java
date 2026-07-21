package kg.equeue.backend.operatorshifts;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OperatorShiftRepository extends JpaRepository<OperatorShiftEntity, UUID> {

    Optional<OperatorShiftEntity> findFirstByOperatorIdAndStatusOrderByOpenedAtDesc(UUID operatorId, OperatorShiftStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OperatorShiftEntity s where s.operatorId = :operatorId and s.status = :status")
    Optional<OperatorShiftEntity> findOpenByOperatorIdForUpdate(UUID operatorId, OperatorShiftStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OperatorShiftEntity s where s.windowId = :windowId and s.status = :status")
    Optional<OperatorShiftEntity> findOpenByWindowIdForUpdate(UUID windowId, OperatorShiftStatus status);
}
