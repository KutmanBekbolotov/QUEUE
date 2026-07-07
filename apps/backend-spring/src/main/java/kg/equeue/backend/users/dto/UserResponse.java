package kg.equeue.backend.users.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.users.UserStatus;

public record UserResponse(
        UUID id,
        String username,
        String fullName,
        String email,
        String phone,
        UUID departmentId,
        UserStatus status,
        int tokenVersion,
        List<String> roles,
        Instant createdAt,
        Instant updatedAt
) {
}
