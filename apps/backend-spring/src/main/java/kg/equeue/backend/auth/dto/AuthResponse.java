package kg.equeue.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.users.UserStatus;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant expiresAt,
        List<String> roles,
        List<String> permissions,
        UUID id,
        String username,
        String fullName,
        String email,
        String phone,
        UUID departmentId,
        UUID windowId,
        List<UUID> serviceIds,
        List<String> serviceCodes,
        UserStatus status
) {
    @JsonProperty("services")
    public List<String> services() {
        return serviceCodes;
    }
}

