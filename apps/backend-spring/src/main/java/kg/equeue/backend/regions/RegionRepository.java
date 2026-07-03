package kg.equeue.backend.regions;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<RegionEntity, UUID> {
    boolean existsByCode(String code);
    List<RegionEntity> findAllByOrderByNameAsc();
}

