package kg.equeue.backend.auth.dto;

import java.time.Instant;
import java.util.List;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant expiresAt,
        List<String> roles,
        List<String> permissions
) {
}

