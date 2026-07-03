package kg.equeue.backend.auth.dto;

import java.util.List;
import java.util.UUID;
import kg.equeue.backend.users.UserStatus;

public record MeResponse(
        UUID id,
        String username,
        String fullName,
        UserStatus status,
        List<String> roles,
        List<String> permissions
) {
}

