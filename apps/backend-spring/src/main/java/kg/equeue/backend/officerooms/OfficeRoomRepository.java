package kg.equeue.backend.officerooms;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfficeRoomRepository extends JpaRepository<OfficeRoomEntity, UUID> {
    List<OfficeRoomEntity> findByDepartmentIdOrderByCodeAsc(UUID departmentId);
}

