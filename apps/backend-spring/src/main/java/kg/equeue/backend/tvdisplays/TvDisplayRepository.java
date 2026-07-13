package kg.equeue.backend.tvdisplays;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TvDisplayRepository extends JpaRepository<TvDisplayEntity, UUID> {
    List<TvDisplayEntity> findByDepartmentIdAndActiveTrueOrderByCodeAsc(UUID departmentId);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);
}
