package kg.equeue.backend.roles.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String code,
        String name,
        boolean systemRole,
        List<String> permissions,
        Instant createdAt,
        Instant updatedAt
) {
}

