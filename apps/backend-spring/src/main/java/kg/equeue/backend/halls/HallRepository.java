package kg.equeue.backend.halls;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HallRepository extends JpaRepository<HallEntity, UUID> {
    List<HallEntity> findByDepartmentIdOrderByCodeAsc(UUID departmentId);
}

