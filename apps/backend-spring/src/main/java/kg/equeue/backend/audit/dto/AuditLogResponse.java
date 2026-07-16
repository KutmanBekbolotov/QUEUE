package kg.equeue.backend.audit.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String actorType,
        UUID actorId,
        String actorName,
        List<String> actorRoles,
        String action,
        String entityType,
        UUID entityId,
        String oldValue,
        String newValue,
        String ip,
        String userAgent,
        String source,
        String requestId,
        Instant createdAt
) {
}
