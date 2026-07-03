package kg.equeue.backend.integrationclients;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntegrationRequestRepository extends JpaRepository<IntegrationRequestEntity, UUID> {

    @Query(value = """
            SELECT *
            FROM integration_requests r
            WHERE r.client_code = :clientCode
              AND ((:idempotencyKey IS NOT NULL AND r.idempotency_key = :idempotencyKey)
                   OR (:externalRequestId IS NOT NULL AND r.external_request_id = :externalRequestId))
            ORDER BY r.created_at ASC
            LIMIT 1
            FOR UPDATE
            """, nativeQuery = true)
    Optional<IntegrationRequestEntity> findExistingForUpdate(@Param("clientCode") String clientCode,
                                                             @Param("idempotencyKey") String idempotencyKey,
                                                             @Param("externalRequestId") String externalRequestId);
}
