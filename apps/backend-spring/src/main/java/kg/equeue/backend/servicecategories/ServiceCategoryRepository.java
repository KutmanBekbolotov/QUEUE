package kg.equeue.backend.servicecategories;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategoryEntity, UUID> {
    boolean existsByCode(String code);
    List<ServiceCategoryEntity> findAllByOrderByNameAsc();
}

