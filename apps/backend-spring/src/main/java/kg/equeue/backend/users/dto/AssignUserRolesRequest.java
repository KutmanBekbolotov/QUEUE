package kg.equeue.backend.users.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record AssignUserRolesRequest(@NotEmpty Set<String> roleCodes) {
}

