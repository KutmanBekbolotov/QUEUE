package kg.equeue.backend.users.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Size(max = 120) String username,
        @NotBlank @Size(min = 8, max = 120) String password,
        @Size(max = 255) String fullName,
        @Size(max = 255) String email,
        @Size(max = 64) String phone,
        UUID departmentId,
        Set<String> roleCodes,
        String windowId,
        @JsonAlias({"services", "serviceCodes"}) Set<String> serviceIds
) {
}
