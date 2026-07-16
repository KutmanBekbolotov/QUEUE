package kg.equeue.backend.users;

import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    boolean existsByUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserEntity> findByUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"roles"})
    List<UserEntity> findAllByIdIn(Collection<UUID> ids);
}
