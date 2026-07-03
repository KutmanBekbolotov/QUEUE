package kg.equeue.backend.reports;

import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kg.equeue.backend.bookings.BookingSource;
import kg.equeue.backend.bookings.BookingStatus;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.reports.ReportDtos.ReportFilter;
import kg.equeue.backend.tickets.TicketSource;
import kg.equeue.backend.tickets.TicketStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReportFilterValidator {

    public static final int STANDARD_MAX_DAYS = 366;
    public static final int DETAIL_MAX_DAYS = 93;
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 500;

    private static final Set<String> SOURCES = new HashSet<>();
    private static final Set<String> TICKET_STATUSES = new HashSet<>();
    private static final Set<String> BOOKING_STATUSES = new HashSet<>();

    static {
        Arrays.stream(TicketSource.values()).map(Enum::name).forEach(SOURCES::add);
        Arrays.stream(BookingSource.values()).map(Enum::name).forEach(SOURCES::add);
        Arrays.stream(TicketStatus.values()).map(Enum::name).forEach(TICKET_STATUSES::add);
        Arrays.stream(BookingStatus.values()).map(Enum::name).forEach(BOOKING_STATUSES::add);
    }

    private final ReportPermissionService permissionService;

    public ReportFilterValidator(ReportPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public ReportCriteria validate(ReportFilter filter, ReportType reportType, boolean detailed, boolean export) {
        if (filter == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_INVALID_DATE_RANGE", "Report filters are required");
        }
        if (export) {
            permissionService.requireExport();
        } else {
            permissionService.requireRead();
        }
        validateDates(filter, detailed, permissionService.hasGlobalReportScope());
        validateEnums(filter);
        validatePersonalData(filter, export);

        int page = filter.getPage() == null || filter.getPage() < 0 ? 0 : filter.getPage();
        int size = filter.getSize() == null || filter.getSize() < 1 ? DEFAULT_PAGE_SIZE : Math.min(filter.getSize(), MAX_PAGE_SIZE);
        filter.setPage(page);
        filter.setSize(size);

        List<UUID> departments = permissionService.resolveDepartmentScope(filter.getDepartmentId());
        boolean allDepartments = departments == null;
        return new ReportCriteria(
                filter,
                departments,
                allDepartments,
                filter.includePersonalData(),
                permissionService.hasGlobalReportScope(),
                permissionService.currentUserId(),
                page,
                size
        );
    }

    private void validateDates(ReportFilter filter, boolean detailed, boolean admin) {
        if (filter.getDateFrom() == null || filter.getDateTo() == null || filter.getDateFrom().isAfter(filter.getDateTo())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_INVALID_DATE_RANGE", "dateFrom must be before or equal to dateTo");
        }
        long days = ChronoUnit.DAYS.between(filter.getDateFrom(), filter.getDateTo()) + 1;
        int max = detailed && !admin ? DETAIL_MAX_DAYS : STANDARD_MAX_DAYS;
        if (days > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_DATE_RANGE_TOO_LARGE", "Report date range is too large");
        }
    }

    private void validateEnums(ReportFilter filter) {
        if (filter.getSource() != null && !SOURCES.contains(filter.getSource())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unsupported report source");
        }
        if (filter.getTicketStatus() != null && !TICKET_STATUSES.contains(filter.getTicketStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unsupported ticket status");
        }
        if (filter.getBookingStatus() != null && !BOOKING_STATUSES.contains(filter.getBookingStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unsupported booking status");
        }
    }

    private void validatePersonalData(ReportFilter filter, boolean export) {
        if (!filter.includePersonalData()) {
            return;
        }
        if (!permissionService.canViewPersonalData()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REPORT_PERSONAL_DATA_FORBIDDEN", "Personal data report access is forbidden");
        }
        if (export && !permissionService.canExportPersonalData()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REPORT_PERSONAL_DATA_FORBIDDEN", "Personal data export is forbidden");
        }
    }
}
