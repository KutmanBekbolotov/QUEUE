package kg.equeue.backend.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueServiceRepository extends JpaRepository<QueueServiceEntity, UUID> {
    boolean existsByCode(String code);
    Optional<QueueServiceEntity> findByCodeIgnoreCase(String code);
    List<QueueServiceEntity> findAllByOrderByNameAsc();
    List<QueueServiceEntity> findByCategoryIdOrderByNameAsc(UUID categoryId);
}

