package kg.equeue.backend.users.dto;

import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record UpdateUserRequest(
        @Size(max = 120) String username,
        @Size(min = 8, max = 120) String password,
        @Size(max = 255) String fullName,
        @Size(max = 255) String email,
        @Size(max = 64) String phone,
        UUID departmentId,
        Set<String> roleCodes
) {
}
