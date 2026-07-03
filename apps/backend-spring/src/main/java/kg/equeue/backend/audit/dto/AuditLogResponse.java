package kg.equeue.backend.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String actorType,
        UUID actorId,
        String action,
        String entityType,
        UUID entityId,
        String ip,
        String source,
        String requestId,
        Instant createdAt
) {
}

