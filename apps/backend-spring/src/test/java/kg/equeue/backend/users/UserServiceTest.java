package kg.equeue.backend.users;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kg.equeue.backend.audit.AuditLogRepository;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.auth.AuthenticatedPrincipal;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.roles.RoleRepository;
import kg.equeue.backend.users.dto.UpdateUserStatusRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserService userService = new UserService(
            userRepository,
            mock(RoleRepository.class),
            mock(PasswordEncoder.class),
            new AuditService(mock(AuditLogRepository.class))
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUserCannotDeactivateOwnAccount() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("admin");
        user.setStatus(UserStatus.ACTIVE);
        when(userRepository.findDetailedById(userId)).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedPrincipal(userId, "admin", 1, UserStatus.ACTIVE, List.of()),
                null,
                List.of()
        ));

        assertThatThrownBy(() -> userService.updateStatus(
                userId,
                new UpdateUserStatusRequest(UserStatus.BLOCKED),
                null
        ))
                .isInstanceOf(ApiException.class)
                .hasMessage("Current user cannot deactivate own account");

        verify(userRepository, never()).save(user);
    }
}
