package kg.equeue.backend.users.dto;

import jakarta.validation.constraints.NotNull;
import kg.equeue.backend.users.UserStatus;

public record UpdateUserStatusRequest(@NotNull UserStatus status) {
}

