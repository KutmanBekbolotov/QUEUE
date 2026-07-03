package kg.equeue.backend.roles.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record AssignRolePermissionsRequest(@NotEmpty Set<String> permissionCodes) {
}

