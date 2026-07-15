package kg.equeue.backend.users.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        UUID windowId,
        List<UUID> serviceIds,
        List<String> serviceCodes,
        UserStatus status,
        int tokenVersion,
        List<String> roles,
        Instant createdAt,
        Instant updatedAt
) {
    @JsonProperty("services")
    public List<String> services() {
        return serviceCodes;
    }
}
