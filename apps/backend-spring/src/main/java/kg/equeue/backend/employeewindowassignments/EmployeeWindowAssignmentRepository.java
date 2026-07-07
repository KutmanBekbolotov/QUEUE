package kg.equeue.backend.employeewindowassignments;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeWindowAssignmentRepository extends JpaRepository<EmployeeWindowAssignmentEntity, UUID> {
    Optional<EmployeeWindowAssignmentEntity> findByUserIdAndServiceWindowId(UUID userId, UUID serviceWindowId);
    List<EmployeeWindowAssignmentEntity> findByServiceWindowId(UUID serviceWindowId);
    boolean existsByUserIdAndServiceWindowIdAndActiveTrue(UUID userId, UUID serviceWindowId);
}
