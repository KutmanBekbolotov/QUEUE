package kg.equeue.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.UUID;
import kg.equeue.backend.auth.AuthenticatedPrincipal;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.common.DepartmentScopeService;
import kg.equeue.backend.users.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class DepartmentScopePermissionTest {

    private final FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate();
    private final DepartmentScopeService service = new DepartmentScopeService(jdbcTemplate);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void superAdminCanAccessAllDepartmentsWithoutScopeLookup() {
        authenticate(UUID.randomUUID(), "ROLE_SUPER_ADMIN");

        assertThat(service.canAccessDepartment(UUID.randomUUID())).isTrue();
        assertThat(jdbcTemplate.queryCount).isZero();
    }

    @Test
    void departmentManagerCanAccessAssignedDepartment() {
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        authenticate(userId, "ROLE_DEPARTMENT_MANAGER");
        jdbcTemplate.departmentScopeResult = 1;

        assertThat(service.canAccessDepartment(departmentId)).isTrue();
    }

    @Test
    void departmentManagerCannotAccessUnassignedDepartmentOrWindow() {
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        authenticate(userId, "ROLE_DEPARTMENT_MANAGER");
        jdbcTemplate.departmentScopeResult = 0;
        jdbcTemplate.windowAssignmentResult = 0;

        assertThat(service.canAccessDepartment(departmentId)).isFalse();
        assertThatThrownBy(() -> service.requireDepartmentAccess(departmentId))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("DEPARTMENT_SCOPE_DENIED"));
    }

    private void authenticate(UUID userId, String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        var principal = new AuthenticatedPrincipal(userId, "scoped-user", 1, UserStatus.ACTIVE, granted);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, granted));
    }

    static class FakeJdbcTemplate extends JdbcTemplate {
        int departmentScopeResult;
        int windowAssignmentResult;
        int queryCount;

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            queryCount++;
            Integer result = sql.contains("user_department_scopes") ? departmentScopeResult : windowAssignmentResult;
            return requiredType.cast(result);
        }
    }
}
