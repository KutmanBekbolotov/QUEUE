package kg.equeue.backend.employeewindowassignments;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeWindowAssignmentRepository extends JpaRepository<EmployeeWindowAssignmentEntity, UUID> {
    Optional<EmployeeWindowAssignmentEntity> findByUserIdAndServiceWindowId(UUID userId, UUID serviceWindowId);
    List<EmployeeWindowAssignmentEntity> findByUserIdAndActiveTrueOrderByAssignedAtDesc(UUID userId);
    List<EmployeeWindowAssignmentEntity> findByServiceWindowId(UUID serviceWindowId);
    Optional<EmployeeWindowAssignmentEntity> findFirstByServiceWindowIdAndActiveTrueOrderByAssignedAtDesc(UUID serviceWindowId);
    boolean existsByUserIdAndServiceWindowIdAndActiveTrue(UUID userId, UUID serviceWindowId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmployeeWindowAssignmentEntity a set a.active = false where a.userId = :userId and a.active = true")
    int deactivateActiveByUserId(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmployeeWindowAssignmentEntity a set a.active = false where a.serviceWindowId = :serviceWindowId and a.active = true")
    int deactivateActiveByServiceWindowId(UUID serviceWindowId);
}
