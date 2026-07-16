package kg.equeue.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kg.equeue.backend.audit.LoginAuditLogEntity;
import kg.equeue.backend.audit.LoginAuditLogRepository;
import kg.equeue.backend.auth.dto.AuthResponse;
import kg.equeue.backend.auth.dto.LoginRequest;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.config.SecurityProperties;
import kg.equeue.backend.permissions.PermissionEntity;
import kg.equeue.backend.roles.RoleEntity;
import kg.equeue.backend.users.UserDepartmentScopeRepository;
import kg.equeue.backend.users.UserAssignmentService;
import kg.equeue.backend.users.UserEntity;
import kg.equeue.backend.users.UserRepository;
import kg.equeue.backend.users.UserStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {

    private final UUID departmentId = UUID.randomUUID();
    private final UUID windowId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = org.mockito.Mockito.mock(RefreshTokenRepository.class);
    private final LoginAuditLogRepository loginAuditLogRepository = org.mockito.Mockito.mock(LoginAuditLogRepository.class);
    private final UserDepartmentScopeRepository departmentScopeRepository = new UserDepartmentScopeRepository(null) {
        @Override
        public UUID primaryDepartmentId(UUID userId) {
            return departmentId;
        }
    };
    private final UserAssignmentService userAssignmentService = org.mockito.Mockito.mock(UserAssignmentService.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecurityProperties securityProperties = securityProperties();
    private final AuthService authService;

    AuthServiceTest() {
        when(userAssignmentService.assignments(any(UUID.class), eq(departmentId)))
                .thenReturn(new UserAssignmentService.AssignmentSnapshot(windowId, List.of(serviceId), List.of("VS")));
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                loginAuditLogRepository,
                departmentScopeRepository,
                userAssignmentService,
                passwordEncoder,
                new JwtService(securityProperties),
                securityProperties
        );
    }

    @Test
    void loginReturnsAccessAndRefreshTokens() {
        UserEntity user = activeUser();
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(new LoginRequest("admin", "ChangeMe123!"), new MockHttpServletRequest());

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.roles()).containsExactly("SUPER_ADMIN");
        assertThat(response.permissions()).containsExactly("USER_READ");
        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.departmentId()).isEqualTo(departmentId);
        assertThat(response.windowId()).isEqualTo(windowId);
        assertThat(response.serviceIds()).containsExactly(serviceId);
        assertThat(response.serviceCodes()).containsExactly("VS");
        ArgumentCaptor<RefreshTokenEntity> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        assertThat(refreshTokenCaptor.getValue().getTokenHash()).isNotEqualTo(response.refreshToken());
        assertThat(refreshTokenCaptor.getValue().getTokenHash()).isEqualTo(authService.hash(response.refreshToken()));
        verify(loginAuditLogRepository).save(any(LoginAuditLogEntity.class));
    }

    @Test
    void loginRejectsBadPasswordAndAuditsFailure() {
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "bad-password"), new MockHttpServletRequest()))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid username or password");

        ArgumentCaptor<LoginAuditLogEntity> auditCaptor = ArgumentCaptor.forClass(LoginAuditLogEntity.class);
        verify(loginAuditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().isSuccess()).isFalse();
        assertThat(auditCaptor.getValue().getReason()).isEqualTo("BAD_CREDENTIALS");
    }

    @Test
    void loginRejectsBlockedUserAndDoesNotIssueRefreshToken() {
        UserEntity user = activeUser();
        user.setStatus(UserStatus.BLOCKED);
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "ChangeMe123!"), new MockHttpServletRequest()))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("USER_NOT_ACTIVE"));

        verify(refreshTokenRepository, never()).save(any());
        ArgumentCaptor<LoginAuditLogEntity> auditCaptor = ArgumentCaptor.forClass(LoginAuditLogEntity.class);
        verify(loginAuditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().isSuccess()).isFalse();
        assertThat(auditCaptor.getValue().getReason()).isEqualTo("USER_NOT_ACTIVE");
    }

    @Test
    void meReturnsUserAssignments() {
        UserEntity user = activeUser();
        when(userRepository.findDetailedById(user.getId())).thenReturn(Optional.of(user));

        var response = authService.me(new AuthenticatedPrincipal(
                user.getId(), user.getUsername(), user.getTokenVersion(), user.getStatus(), List.of()
        ));

        assertThat(response.email()).isEqualTo("admin@example.com");
        assertThat(response.phone()).isEqualTo("+996555000000");
        assertThat(response.departmentId()).isEqualTo(departmentId);
        assertThat(response.windowId()).isEqualTo(windowId);
        assertThat(response.serviceIds()).containsExactly(serviceId);
        assertThat(response.serviceCodes()).containsExactly("VS");
    }

    private UserEntity activeUser() {
        PermissionEntity permission = new PermissionEntity();
        permission.setCode("USER_READ");

        RoleEntity role = new RoleEntity();
        role.setCode("SUPER_ADMIN");
        role.getPermissions().add(permission);

        UserEntity user = new UserEntity();
        user.setId(java.util.UUID.randomUUID());
        user.setUsername("admin");
        user.setFullName("Admin User");
        user.setEmail("admin@example.com");
        user.setPhone("+996555000000");
        user.setPasswordHash(passwordEncoder.encode("ChangeMe123!"));
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(1);
        user.getRoles().add(role);
        return user;
    }

    private SecurityProperties securityProperties() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret("test-secret-that-is-long-enough-for-hs256");
        return properties;
    }
}
