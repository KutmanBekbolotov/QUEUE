package kg.equeue.backend.reports;

import java.util.List;
import java.util.UUID;
import kg.equeue.backend.reports.ReportDtos.ReportFilter;

public record ReportCriteria(
        ReportFilter filter,
        List<UUID> departmentIds,
        boolean allDepartments,
        boolean includePersonalData,
        boolean admin,
        UUID userId,
        int page,
        int size
) {
    public ReportCriteria withPageAndSize(int newPage, int newSize) {
        return new ReportCriteria(filter, departmentIds, allDepartments, includePersonalData, admin, userId, newPage, newSize);
    }
}
