package kg.equeue.backend.roles;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

    Optional<RoleEntity> findByCode(String code);

    @EntityGraph(attributePaths = "permissions")
    List<RoleEntity> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = "permissions")
    List<RoleEntity> findByCodeIn(Collection<String> codes);
}

