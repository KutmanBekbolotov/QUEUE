package kg.equeue.backend.common;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DepartmentScopeService {

    private final JdbcTemplate jdbcTemplate;

    public DepartmentScopeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void requireDepartmentAccess(UUID departmentId) {
        if (!canAccessDepartment(departmentId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DEPARTMENT_SCOPE_DENIED", "Department is outside of current user scope");
        }
    }

    public boolean canAccessDepartment(UUID departmentId) {
        if (departmentId == null || CurrentUser.hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_INTEGRATION_SERVICE")) {
            return true;
        }
        UUID userId = CurrentUser.idOrNull();
        if (userId == null) {
            return false;
        }
        Integer scoped = jdbcTemplate.queryForObject("""
                SELECT CASE WHEN EXISTS (
                  SELECT 1 FROM user_department_scopes
                  WHERE user_id = ? AND department_id = ?
                ) THEN 1 ELSE 0 END
                """, Integer.class, userId, departmentId);
        if (scoped != null && scoped == 1) {
            return true;
        }
        Integer windowAssigned = jdbcTemplate.queryForObject("""
                SELECT CASE WHEN EXISTS (
                  SELECT 1
                  FROM employee_window_assignments ewa
                  JOIN service_windows sw ON sw.id = ewa.service_window_id
                  WHERE ewa.user_id = ? AND sw.department_id = ? AND ewa.active = true
                ) THEN 1 ELSE 0 END
                """, Integer.class, userId, departmentId);
        return windowAssigned != null && windowAssigned == 1;
    }

    public void requireWindowAccess(UUID windowId) {
        if (CurrentUser.hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_INTEGRATION_SERVICE")) {
            return;
        }
        UUID userId = CurrentUser.idOrNull();
        if (userId == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WINDOW_SCOPE_DENIED", "Window is outside of current user scope");
        }
        Integer assigned = jdbcTemplate.queryForObject("""
                SELECT CASE WHEN EXISTS (
                  SELECT 1 FROM employee_window_assignments
                  WHERE user_id = ? AND service_window_id = ? AND active = true
                ) THEN 1 ELSE 0 END
                """, Integer.class, userId, windowId);
        if (assigned == null || assigned != 1) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WINDOW_SCOPE_DENIED", "Window is outside of current user scope");
        }
    }

    public boolean isAssignedToService(UUID serviceId) {
        if (CurrentUser.hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_DEPARTMENT_MANAGER", "ROLE_INTEGRATION_SERVICE")) {
            return true;
        }
        UUID userId = CurrentUser.idOrNull();
        if (userId == null) {
            return false;
        }
        Integer assigned = jdbcTemplate.queryForObject("""
                SELECT CASE WHEN EXISTS (
                  SELECT 1 FROM employee_service_assignments
                  WHERE user_id = ? AND service_id = ? AND active = true
                ) THEN 1 ELSE 0 END
                """, Integer.class, userId, serviceId);
        return assigned != null && assigned == 1;
    }
}
