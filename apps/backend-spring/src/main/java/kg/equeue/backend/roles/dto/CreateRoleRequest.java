package kg.equeue.backend.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateRoleRequest(
        @NotBlank @Size(max = 120) String code,
        @NotBlank @Size(max = 200) String name,
        Set<String> permissionCodes
) {
}

