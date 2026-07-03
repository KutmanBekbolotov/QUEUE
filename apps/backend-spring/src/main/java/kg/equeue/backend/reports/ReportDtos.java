package kg.equeue.backend.reports;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public final class ReportDtos {

    private ReportDtos() {
    }

    @Schema(name = "ReportFilter")
    public static class ReportFilter {
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dateFrom;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dateTo;

        private UUID regionId;
        private UUID departmentId;
        private UUID employeeId;
        private UUID windowId;
        private UUID serviceCategoryId;
        private UUID serviceId;
        private String source;
        private String ticketStatus;
        private String bookingStatus;
        private UUID cancellationReasonId;
        private String groupBy;
        private Boolean includePersonalData = Boolean.FALSE;
        private Integer page = 0;
        private Integer size = 50;

        public LocalDate getDateFrom() {
            return dateFrom;
        }

        public void setDateFrom(LocalDate dateFrom) {
            this.dateFrom = dateFrom;
        }

        public LocalDate getDateTo() {
            return dateTo;
        }

        public void setDateTo(LocalDate dateTo) {
            this.dateTo = dateTo;
        }

        public UUID getRegionId() {
            return regionId;
        }

        public void setRegionId(UUID regionId) {
            this.regionId = regionId;
        }

        public UUID getDepartmentId() {
            return departmentId;
        }

        public void setDepartmentId(UUID departmentId) {
            this.departmentId = departmentId;
        }

        public UUID getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(UUID employeeId) {
            this.employeeId = employeeId;
        }

        public UUID getWindowId() {
            return windowId;
        }

        public void setWindowId(UUID windowId) {
            this.windowId = windowId;
        }

        public UUID getServiceCategoryId() {
            return serviceCategoryId;
        }

        public void setServiceCategoryId(UUID serviceCategoryId) {
            this.serviceCategoryId = serviceCategoryId;
        }

        public UUID getServiceId() {
            return serviceId;
        }

        public void setServiceId(UUID serviceId) {
            this.serviceId = serviceId;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = blankToNull(source);
        }

        public String getTicketStatus() {
            return ticketStatus;
        }

        public void setTicketStatus(String ticketStatus) {
            this.ticketStatus = blankToNull(ticketStatus);
        }

        public String getBookingStatus() {
            return bookingStatus;
        }

        public void setBookingStatus(String bookingStatus) {
            this.bookingStatus = blankToNull(bookingStatus);
        }

        public UUID getCancellationReasonId() {
            return cancellationReasonId;
        }

        public void setCancellationReasonId(UUID cancellationReasonId) {
            this.cancellationReasonId = cancellationReasonId;
        }

        public String getGroupBy() {
            return groupBy;
        }

        public void setGroupBy(String groupBy) {
            this.groupBy = blankToNull(groupBy);
        }

        public Boolean getIncludePersonalData() {
            return includePersonalData;
        }

        public void setIncludePersonalData(Boolean includePersonalData) {
            this.includePersonalData = includePersonalData == null ? Boolean.FALSE : includePersonalData;
        }

        public Integer getPage() {
            return page;
        }

        public void setPage(Integer page) {
            this.page = page;
        }

        public Integer getSize() {
            return size;
        }

        public void setSize(Integer size) {
            this.size = size;
        }

        @JsonIgnore
        public boolean includePersonalData() {
            return Boolean.TRUE.equals(includePersonalData);
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record SummaryResponse(
            long totalTickets,
            long waitingTickets,
            long completedTickets,
            long cancelledTickets,
            long noShowTickets,
            long expiredTickets,
            long totalBookings,
            long completedBookings,
            long cancelledBookings,
            long expiredBookings,
            Double averageWaitingSeconds,
            Double averageServiceSeconds,
            Double averageTicketsPerDay,
            String busiestDepartment,
            String busiestService,
            Integer busiestHour
    ) {
    }

    public record ByRegionRow(
            UUID regionId,
            String regionName,
            long departmentsCount,
            long totalTickets,
            long completedTickets,
            long cancelledTickets,
            long noShowTickets,
            long totalBookings,
            Double averageWaitingSeconds,
            Double averageServiceSeconds
    ) {
    }

    public record ByDepartmentRow(
            UUID departmentId,
            String departmentName,
            String regionName,
            long totalTickets,
            long completedTickets,
            long cancelledTickets,
            long noShowTickets,
            long totalBookings,
            long checkedInBookings,
            Double averageWaitingSeconds,
            Double averageServiceSeconds,
            long activeWindowsCount,
            Double ticketsPerWindowAverage
    ) {
    }

    public record ByEmployeeRow(
            UUID employeeId,
            String employeeFullName,
            UUID departmentId,
            String departmentName,
            long totalServed,
            long completedTickets,
            long cancelledTickets,
            long noShowTickets,
            Double averageServiceSeconds,
            Instant firstServiceAt,
            Instant lastServiceAt
    ) {
    }

    public record ByServiceRow(
            UUID serviceId,
            String serviceNameRu,
            String serviceNameKy,
            UUID categoryId,
            String categoryName,
            long totalTickets,
            long completedTickets,
            long cancelledTickets,
            long noShowTickets,
            long totalBookings,
            Double averageWaitingSeconds,
            Double averageServiceSeconds
    ) {
    }

    public record BySourceRow(
            String source,
            long totalTickets,
            long totalBookings,
            long completedTickets,
            long cancelledTickets,
            Double conversionToCheckInPercent
    ) {
    }

    public record ByStatusRow(
            String status,
            long count,
            Double percentage
    ) {
    }

    public record TimeBucketRow(String bucket, long count) {
    }

    public record WaitingTimeResponse(
            Double averageWaitingSeconds,
            Double medianWaitingSeconds,
            Long minWaitingSeconds,
            Long maxWaitingSeconds,
            Double p90WaitingSeconds,
            List<TimeBucketRow> buckets
    ) {
    }

    public record ServiceTimeResponse(
            Double averageServiceSeconds,
            Double medianServiceSeconds,
            Long minServiceSeconds,
            Long maxServiceSeconds,
            Double p90ServiceSeconds,
            List<TimeBucketRow> buckets
    ) {
    }

    public record CancellationRow(
            UUID cancellationReasonId,
            String cancellationReasonName,
            long count,
            Double percentage,
            List<SimpleMetricRow> departments
    ) {
    }

    public record SimpleMetricRow(
            UUID id,
            String name,
            long count
    ) {
    }

    public record HourMetricRow(
            int hour,
            long count
    ) {
    }

    public record CancellationsResponse(List<CancellationRow> reasons) {
    }

    public record NoShowsResponse(
            long totalNoShows,
            List<SimpleMetricRow> byDepartment,
            List<SimpleMetricRow> byService,
            List<HourMetricRow> byHour,
            Double noShowRate
    ) {
    }

    public record BookingsResponse(
            long totalBookings,
            long confirmed,
            long checkedIn,
            long cancelled,
            long expired,
            long noShow,
            Double checkInRate,
            Double cancellationRate,
            Double expirationRate,
            List<SimpleMetricRow> bySource,
            List<SimpleMetricRow> byDepartment,
            List<SimpleMetricRow> byService
    ) {
    }

    public record WindowWorkloadRow(
            UUID windowId,
            String windowNumber,
            UUID departmentId,
            String departmentName,
            long totalCalled,
            long totalCompleted,
            Double averageServiceSeconds,
            Long activeServiceSeconds,
            Long idleEstimateSeconds,
            Double utilizationPercent
    ) {
    }

    public record WorkloadHourlyRow(
            LocalDate date,
            int hour,
            UUID departmentId,
            long totalTickets,
            long totalBookings,
            long completedTickets,
            Double averageWaitingSeconds,
            Double averageServiceSeconds
    ) {
    }

    public record WorkloadDailyRow(
            LocalDate date,
            UUID departmentId,
            long totalTickets,
            long totalBookings,
            long completedTickets,
            long cancelledTickets,
            long noShowTickets,
            Double averageWaitingSeconds,
            Double averageServiceSeconds
    ) {
    }

    public record TicketDetailRow(
            UUID ticketId,
            String ticketNumber,
            String department,
            String window,
            String service,
            String source,
            String status,
            Instant createdAt,
            Instant calledAt,
            Instant serviceStartedAt,
            Instant serviceCompletedAt,
            Long waitingSeconds,
            Long serviceSeconds,
            String servedByUser,
            String citizenFullName
    ) {
    }

    public record BookingDetailRow(
            UUID bookingId,
            String bookingNumber,
            String source,
            String externalId,
            String department,
            String service,
            LocalDate bookingDate,
            LocalTime bookingStart,
            LocalTime bookingEnd,
            String status,
            String ticketNumber,
            String citizenFullName
    ) {
    }

    public record IntegrationReportRow(
            String clientCode,
            long totalRequests,
            long successfulRequests,
            long failedRequests,
            long duplicateIdempotentRequests,
            long idempotencyConflicts,
            Double averageResponseTimeMs,
            Instant lastRequestAt
    ) {
    }

    public record ExportRequest(
            @NotNull ReportType reportType,
            @NotNull ReportExportFormat format,
            @Valid @NotNull ReportFilter filters
    ) {
    }

    public record ExportResponse(
            UUID id,
            ReportType reportType,
            ReportExportFormat format,
            ReportExportStatus status,
            String fileName,
            Long fileSizeBytes,
            Instant createdAt,
            Instant completedAt,
            String downloadUrl,
            String errorMessage
    ) {
    }
}
