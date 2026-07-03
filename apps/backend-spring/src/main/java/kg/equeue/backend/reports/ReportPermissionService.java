package kg.equeue.backend.reports;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.common.CurrentUser;
import kg.equeue.backend.reports.ReportDtos.ReportFilter;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReportPermissionService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReportPermissionService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void requireRead() {
        if (!CurrentUser.hasAuthority("REPORT_READ") && !CurrentUser.hasAuthority("ROLE_INTEGRATION_SERVICE")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Report read permission is required");
        }
    }

    public void requireExport() {
        if (!CurrentUser.hasAuthority("REPORT_EXPORT") && !CurrentUser.hasAuthority("ROLE_INTEGRATION_SERVICE")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Report export permission is required");
        }
    }

    public boolean canViewPersonalData() {
        return CurrentUser.hasAuthority("REPORT_VIEW_PERSONAL_DATA")
                || CurrentUser.hasAuthority("ROLE_SUPER_ADMIN")
                || CurrentUser.hasAuthority("ROLE_INTEGRATION_SERVICE");
    }

    public boolean canExportPersonalData() {
        return CurrentUser.hasAuthority("REPORT_EXPORT_PERSONAL_DATA")
                || CurrentUser.hasAuthority("ROLE_SUPER_ADMIN");
    }

    public boolean hasGlobalReportScope() {
        return CurrentUser.hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_INTEGRATION_SERVICE");
    }

    public UUID currentUserId() {
        return CurrentUser.idOrNull();
    }

    public List<UUID> resolveDepartmentScope(UUID requestedDepartmentId) {
        if (hasGlobalReportScope()) {
            return requestedDepartmentId == null ? null : List.of(requestedDepartmentId);
        }

        UUID userId = CurrentUser.idOrNull();
        if (userId == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REPORT_SCOPE_FORBIDDEN", "Report scope could not be resolved");
        }

        List<UUID> allowed = jdbcTemplate.queryForList("""
                SELECT DISTINCT department_id
                FROM (
                  SELECT uds.department_id
                  FROM user_department_scopes uds
                  WHERE uds.user_id = :userId
                  UNION
                  SELECT sw.department_id
                  FROM employee_window_assignments ewa
                  JOIN service_windows sw ON sw.id = ewa.service_window_id
                  WHERE ewa.user_id = :userId AND ewa.active = true
                ) scoped
                """, new MapSqlParameterSource("userId", userId), UUID.class);

        if (requestedDepartmentId != null) {
            boolean allowedDepartment = allowed.stream().anyMatch(id -> Objects.equals(id, requestedDepartmentId));
            if (!allowedDepartment) {
                throw new ApiException(HttpStatus.FORBIDDEN, "REPORT_SCOPE_FORBIDDEN", "Department is outside of current report scope");
            }
            return List.of(requestedDepartmentId);
        }
        return allowed;
    }

    public void requireDownloadAccess(ReportExportEntity export, ReportFilter filter) {
        if (hasGlobalReportScope()) {
            return;
        }
        UUID userId = CurrentUser.idOrNull();
        if (userId != null && Objects.equals(userId, export.getRequestedByUserId())) {
            return;
        }
        if (!CurrentUser.hasAuthority("REPORT_READ")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REPORT_SCOPE_FORBIDDEN", "Report export is outside of current scope");
        }
        UUID departmentId = filter == null || filter.getDepartmentId() == null ? export.getDepartmentId() : filter.getDepartmentId();
        if (departmentId == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REPORT_SCOPE_FORBIDDEN", "Report export is outside of current scope");
        }
        resolveDepartmentScope(departmentId);
    }
}
