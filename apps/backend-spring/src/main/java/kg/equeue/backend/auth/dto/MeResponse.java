package kg.equeue.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.users.UserStatus;

public record MeResponse(
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
        List<String> roles,
        List<String> permissions
) {
    @JsonProperty("services")
    public List<String> services() {
        return serviceCodes;
    }
}
