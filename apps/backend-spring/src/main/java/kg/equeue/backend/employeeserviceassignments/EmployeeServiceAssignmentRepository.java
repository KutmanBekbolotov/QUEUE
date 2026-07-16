package kg.equeue.backend.employeeserviceassignments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeServiceAssignmentRepository extends JpaRepository<EmployeeServiceAssignmentEntity, UUID> {
    Optional<EmployeeServiceAssignmentEntity> findByUserIdAndDepartmentIdAndServiceId(UUID userId, UUID departmentId, UUID serviceId);
    List<EmployeeServiceAssignmentEntity> findByUserId(UUID userId);
    List<EmployeeServiceAssignmentEntity> findByUserIdAndActiveTrueOrderByServiceIdAsc(UUID userId);
    List<EmployeeServiceAssignmentEntity> findByUserIdAndDepartmentIdAndActiveTrueOrderByServiceIdAsc(UUID userId, UUID departmentId);
    List<EmployeeServiceAssignmentEntity> findByUserIdAndServiceId(UUID userId, UUID serviceId);
    List<EmployeeServiceAssignmentEntity> findByDepartmentId(UUID departmentId);
    List<EmployeeServiceAssignmentEntity> findByServiceId(UUID serviceId);
}
