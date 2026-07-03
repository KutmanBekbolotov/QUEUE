package kg.equeue.backend.integrationmappings;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExternalDirectoryMappingRepository extends JpaRepository<ExternalDirectoryMappingEntity, UUID> {

    Optional<ExternalDirectoryMappingEntity> findByClientCodeAndEntityTypeAndExternalCodeAndActiveTrue(
            String clientCode,
            ExternalDirectoryMappingEntityType entityType,
            String externalCode
    );

    Optional<ExternalDirectoryMappingEntity> findByClientCodeAndEntityTypeAndExternalCode(
            String clientCode,
            ExternalDirectoryMappingEntityType entityType,
            String externalCode
    );

    @Query("""
            SELECT mapping
            FROM ExternalDirectoryMappingEntity mapping
            WHERE (:clientCode IS NULL OR mapping.clientCode = :clientCode)
              AND (:entityType IS NULL OR mapping.entityType = :entityType)
              AND (:active IS NULL OR mapping.active = :active)
            ORDER BY mapping.clientCode ASC, mapping.entityType ASC, mapping.externalCode ASC
            """)
    List<ExternalDirectoryMappingEntity> search(@Param("clientCode") String clientCode,
                                                @Param("entityType") ExternalDirectoryMappingEntityType entityType,
                                                @Param("active") Boolean active);
}
