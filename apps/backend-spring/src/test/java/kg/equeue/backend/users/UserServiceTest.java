package kg.equeue.backend.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
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
import kg.equeue.backend.roles.RoleEntity;
import kg.equeue.backend.roles.RoleRepository;
import kg.equeue.backend.users.dto.UpdateUserRequest;
import kg.equeue.backend.users.dto.UpdateUserStatusRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final FakeUserDepartmentScopeRepository departmentScopeRepository = new FakeUserDepartmentScopeRepository();
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserService userService = new UserService(
            userRepository,
            roleRepository,
            departmentScopeRepository,
            passwordEncoder,
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

    @Test
    void deleteDisablesUserAndInvalidatesTokens() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("operator");
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(2);
        when(userRepository.findDetailedById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.delete(userId, null);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(user.getTokenVersion()).isEqualTo(3);
        verify(userRepository).save(user);
    }

    @Test
    void currentUserCannotDeleteOwnAccount() {
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

        assertThatThrownBy(() -> userService.delete(userId, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Current user cannot deactivate own account");

        verify(userRepository, never()).save(user);
    }

    @Test
    void updateChangesProfileRolesPasswordAndDepartmentScope() {
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        RoleEntity role = new RoleEntity();
        role.setCode("OPERATOR");
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("old-operator");
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(3);

        when(userRepository.findDetailedById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameIgnoreCase("new-operator")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("encoded-password");
        when(roleRepository.findByCodeIn(anySet())).thenReturn(List.of(role));
        departmentScopeRepository.existingDepartmentId = departmentId;
        departmentScopeRepository.primaryDepartmentId = departmentId;
        when(userRepository.save(user)).thenReturn(user);

        var response = userService.update(
                userId,
                new UpdateUserRequest(
                        "new-operator",
                        "NewPassword123",
                        "New Operator",
                        "operator@example.com",
                        "+996555000000",
                        departmentId,
                        java.util.Set.of("OPERATOR")
                ),
                null
        );

        assertThat(user.getUsername()).isEqualTo("new-operator");
        assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(user.getFullName()).isEqualTo("New Operator");
        assertThat(user.getEmail()).isEqualTo("operator@example.com");
        assertThat(user.getPhone()).isEqualTo("+996555000000");
        assertThat(user.getTokenVersion()).isEqualTo(4);
        assertThat(response.departmentId()).isEqualTo(departmentId);
        assertThat(response.roles()).containsExactly("OPERATOR");
        assertThat(departmentScopeRepository.replacedUserId).isEqualTo(userId);
        assertThat(departmentScopeRepository.replacedDepartmentId).isEqualTo(departmentId);
        verify(userRepository).save(user);
    }

    @Test
    void deviceRolesCannotBeAssignedToLoginUsers() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("device-user");
        user.setStatus(UserStatus.ACTIVE);
        when(userRepository.findDetailedById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.update(
                userId,
                new UpdateUserRequest(null, null, null, null, null, null, java.util.Set.of("TV_DEVICE")),
                null
        ))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("DEVICE_ROLE_NOT_ASSIGNABLE"));

        verify(roleRepository, never()).findByCodeIn(anySet());
        verify(userRepository, never()).save(user);
    }

    private static class FakeUserDepartmentScopeRepository extends UserDepartmentScopeRepository {
        UUID existingDepartmentId;
        UUID primaryDepartmentId;
        UUID replacedUserId;
        UUID replacedDepartmentId;

        FakeUserDepartmentScopeRepository() {
            super(null);
        }

        @Override
        public UUID primaryDepartmentId(UUID userId) {
            return primaryDepartmentId;
        }

        @Override
        public boolean departmentExists(UUID departmentId) {
            return departmentId.equals(existingDepartmentId);
        }

        @Override
        public void replacePrimaryDepartment(UUID userId, UUID departmentId) {
            replacedUserId = userId;
            replacedDepartmentId = departmentId;
        }
    }
}
