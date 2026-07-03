package kg.equeue.backend.departments;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<DepartmentEntity, UUID> {
    boolean existsByCode(String code);
    List<DepartmentEntity> findAllByOrderByNameAsc();
    List<DepartmentEntity> findByRegionIdOrderByNameAsc(UUID regionId);
}

