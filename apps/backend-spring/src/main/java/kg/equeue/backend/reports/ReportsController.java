package kg.equeue.backend.reports;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.reports.ReportDtos.BookingDetailRow;
import kg.equeue.backend.reports.ReportDtos.BookingsResponse;
import kg.equeue.backend.reports.ReportDtos.ByDepartmentRow;
import kg.equeue.backend.reports.ReportDtos.ByEmployeeRow;
import kg.equeue.backend.reports.ReportDtos.ByRegionRow;
import kg.equeue.backend.reports.ReportDtos.ByServiceRow;
import kg.equeue.backend.reports.ReportDtos.BySourceRow;
import kg.equeue.backend.reports.ReportDtos.ByStatusRow;
import kg.equeue.backend.reports.ReportDtos.CancellationsResponse;
import kg.equeue.backend.reports.ReportDtos.ExportRequest;
import kg.equeue.backend.reports.ReportDtos.ExportResponse;
import kg.equeue.backend.reports.ReportDtos.IntegrationReportRow;
import kg.equeue.backend.reports.ReportDtos.NoShowsResponse;
import kg.equeue.backend.reports.ReportDtos.PageResponse;
import kg.equeue.backend.reports.ReportDtos.ReportFilter;
import kg.equeue.backend.reports.ReportDtos.ServiceTimeResponse;
import kg.equeue.backend.reports.ReportDtos.SummaryResponse;
import kg.equeue.backend.reports.ReportDtos.TicketDetailRow;
import kg.equeue.backend.reports.ReportDtos.WaitingTimeResponse;
import kg.equeue.backend.reports.ReportDtos.WindowWorkloadRow;
import kg.equeue.backend.reports.ReportDtos.WorkloadDailyRow;
import kg.equeue.backend.reports.ReportDtos.WorkloadHourlyRow;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports")
public class ReportsController {

    private final ReportsService reportsService;
    private final ReportExportService exportService;

    public ReportsController(ReportsService reportsService, ReportExportService exportService) {
        this.reportsService = reportsService;
        this.exportService = exportService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Summary report")
    public SummaryResponse summary(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.summary(filter, request);
    }

    @GetMapping("/by-region")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Report grouped by region")
    public List<ByRegionRow> byRegion(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.byRegion(filter, request);
    }

    @GetMapping("/by-department")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Report grouped by department")
    public List<ByDepartmentRow> byDepartment(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.byDepartment(filter, request);
    }

    @GetMapping("/by-employee")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Report grouped by employee")
    public List<ByEmployeeRow> byEmployee(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.byEmployee(filter, request);
    }

    @GetMapping("/by-service")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Report grouped by service")
    public List<ByServiceRow> byService(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.byService(filter, request);
    }

    @GetMapping("/by-source")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Report grouped by ticket or booking source")
    public List<BySourceRow> bySource(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.bySource(filter, request);
    }

    @GetMapping("/by-status")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Report grouped by ticket status")
    public List<ByStatusRow> byStatus(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.byStatus(filter, request);
    }

    @GetMapping("/waiting-time")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Waiting time report")
    public WaitingTimeResponse waitingTime(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.waitingTime(filter, request);
    }

    @GetMapping("/service-time")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Service time report")
    public ServiceTimeResponse serviceTime(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.serviceTime(filter, request);
    }

    @GetMapping("/cancellations")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Cancellation reasons report")
    public CancellationsResponse cancellations(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.cancellations(filter, request);
    }

    @GetMapping("/no-shows")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "No-shows report")
    public NoShowsResponse noShows(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.noShows(filter, request);
    }

    @GetMapping("/bookings")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Bookings report")
    public BookingsResponse bookings(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.bookings(filter, request);
    }

    @GetMapping("/window-workload")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Window workload report")
    public List<WindowWorkloadRow> windowWorkload(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.windowWorkload(filter, request);
    }

    @GetMapping("/workload/hourly")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Department workload by hour")
    public List<WorkloadHourlyRow> workloadHourly(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.workloadHourly(filter, request);
    }

    @GetMapping("/workload/daily")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Department workload by day")
    public List<WorkloadDailyRow> workloadDaily(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.workloadDaily(filter, request);
    }

    @GetMapping("/tickets")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Detailed paginated ticket report")
    public PageResponse<TicketDetailRow> tickets(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.ticketDetails(filter, request);
    }

    @GetMapping("/bookings/details")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Detailed paginated booking report")
    public PageResponse<BookingDetailRow> bookingDetails(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.bookingDetails(filter, request);
    }

    @GetMapping("/integrations")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Integration request statistics")
    public List<IntegrationReportRow> integrations(@Valid @ModelAttribute ReportFilter filter, HttpServletRequest request) {
        return reportsService.integrations(filter, request);
    }

    @PostMapping("/export")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    @Operation(summary = "Request a report export")
    public ExportResponse export(@Valid @RequestBody ExportRequest request, HttpServletRequest httpRequest) {
        return exportService.requestExport(request, httpRequest);
    }

    @GetMapping("/export/{id}")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Read report export status")
    public ExportResponse exportStatus(@PathVariable UUID id) {
        return exportService.getStatus(id);
    }

    @GetMapping("/export/{id}/download")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @Operation(summary = "Download a completed report export")
    public ResponseEntity<Resource> download(@PathVariable UUID id, HttpServletRequest request) {
        return exportService.download(id, request);
    }
}
