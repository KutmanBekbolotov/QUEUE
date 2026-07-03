package kg.equeue.backend.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.reports.ReportDtos.ReportFilter;
import org.junit.jupiter.api.Test;

class ReportFilterValidatorTest {

    private final FakePermissionService permissions = new FakePermissionService();
    private final ReportFilterValidator validator = new ReportFilterValidator(permissions);

    @Test
    void rejectsInvalidDateRange() {
        ReportFilter filter = filter(LocalDate.parse("2026-07-10"), LocalDate.parse("2026-07-01"));

        assertThatThrownBy(() -> validator.validate(filter, ReportType.SUMMARY, false, false))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("REPORT_INVALID_DATE_RANGE"));
    }

    @Test
    void rejectsDetailedRangeOverNinetyThreeDaysForScopedUser() {
        permissions.globalScope = false;
        ReportFilter filter = filter(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-04-30"));

        assertThatThrownBy(() -> validator.validate(filter, ReportType.TICKETS_DETAIL, true, false))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("REPORT_DATE_RANGE_TOO_LARGE"));
    }

    @Test
    void rejectsPersonalDataWithoutPermission() {
        permissions.canViewPersonalData = false;
        ReportFilter filter = filter(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"));
        filter.setIncludePersonalData(true);

        assertThatThrownBy(() -> validator.validate(filter, ReportType.SUMMARY, false, false))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("REPORT_PERSONAL_DATA_FORBIDDEN"));
    }

    @Test
    void appliesScopedDepartmentsAndCapsPageSize() {
        UUID departmentId = UUID.randomUUID();
        permissions.departmentScope = List.of(departmentId);
        ReportFilter filter = filter(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"));
        filter.setSize(10_000);

        ReportCriteria criteria = validator.validate(filter, ReportType.SUMMARY, false, false);

        assertThat(criteria.allDepartments()).isFalse();
        assertThat(criteria.departmentIds()).containsExactly(departmentId);
        assertThat(criteria.size()).isEqualTo(ReportFilterValidator.MAX_PAGE_SIZE);
    }

    private ReportFilter filter(LocalDate from, LocalDate to) {
        ReportFilter filter = new ReportFilter();
        filter.setDateFrom(from);
        filter.setDateTo(to);
        return filter;
    }

    static class FakePermissionService extends ReportPermissionService {
        boolean globalScope;
        boolean canViewPersonalData = true;
        boolean canExportPersonalData = true;
        List<UUID> departmentScope = List.of();

        FakePermissionService() {
            super(null);
        }

        @Override
        public void requireRead() {
        }

        @Override
        public void requireExport() {
        }

        @Override
        public boolean canViewPersonalData() {
            return canViewPersonalData;
        }

        @Override
        public boolean canExportPersonalData() {
            return canExportPersonalData;
        }

        @Override
        public boolean hasGlobalReportScope() {
            return globalScope;
        }

        @Override
        public UUID currentUserId() {
            return null;
        }

        @Override
        public List<UUID> resolveDepartmentScope(UUID requestedDepartmentId) {
            return requestedDepartmentId == null ? departmentScope : List.of(requestedDepartmentId);
        }
    }
}
