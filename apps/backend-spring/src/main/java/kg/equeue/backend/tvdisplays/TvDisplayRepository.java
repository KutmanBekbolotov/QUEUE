package kg.equeue.backend.tvdisplays;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TvDisplayRepository extends JpaRepository<TvDisplayEntity, UUID> {
    Optional<TvDisplayEntity> findFirstByDepartmentIdAndActiveTrue(UUID departmentId);
}
