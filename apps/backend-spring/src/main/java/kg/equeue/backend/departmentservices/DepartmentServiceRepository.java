package kg.equeue.backend.departmentservices;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentServiceRepository extends JpaRepository<DepartmentServiceEntity, UUID> {
    List<DepartmentServiceEntity> findByDepartmentIdAndActiveTrueOrderByServiceIdAsc(UUID departmentId);
    Optional<DepartmentServiceEntity> findByDepartmentIdAndServiceId(UUID departmentId, UUID serviceId);
    boolean existsByDepartmentIdAndServiceIdAndActiveTrue(UUID departmentId, UUID serviceId);
}

