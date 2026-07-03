package kg.equeue.backend.permissions;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<PermissionEntity, UUID> {

    List<PermissionEntity> findAllByOrderByCodeAsc();

    List<PermissionEntity> findByCodeIn(Collection<String> codes);
}

