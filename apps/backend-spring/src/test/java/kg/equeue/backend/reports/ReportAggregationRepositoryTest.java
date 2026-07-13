package kg.equeue.backend.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.reports.ReportDtos.BookingsResponse;
import kg.equeue.backend.reports.ReportDtos.ExportRequest;
import kg.equeue.backend.reports.ReportDtos.ExportResponse;
import kg.equeue.backend.reports.ReportDtos.PageResponse;
import kg.equeue.backend.reports.ReportDtos.ReportFilter;
import kg.equeue.backend.reports.ReportDtos.ServiceTimeResponse;
import kg.equeue.backend.reports.ReportDtos.SummaryResponse;
import kg.equeue.backend.reports.ReportDtos.TicketDetailRow;
import kg.equeue.backend.reports.ReportDtos.WaitingTimeResponse;
import kg.equeue.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReportAggregationRepositoryTest extends PostgresIntegrationTest {

    private static final LocalDate REPORT_DAY = LocalDate.of(2026, 7, 1);

    @Autowired
    private ReportQueryRepository repository;

    @Autowired
    private ReportExportService exportService;

    @Autowired
    private ReportExportRepository exportRepository;

    private CoreData data;
    private UUID cancellationReasonId;

    @BeforeEach
    void seed() {
        data = seedCoreData();
        seedReportRows();
    }

    @Test
    void summaryReportAggregation() {
        SummaryResponse response = repository.summary(criteria(false));

        assertThat(response.totalTickets()).isEqualTo(4);
        assertThat(response.waitingTickets()).isEqualTo(1);
        assertThat(response.completedTickets()).isEqualTo(1);
        assertThat(response.cancelledTickets()).isEqualTo(1);
        assertThat(response.noShowTickets()).isEqualTo(1);
        assertThat(response.totalBookings()).isEqualTo(4);
        assertThat(response.completedBookings()).isEqualTo(1);
        assertThat(response.cancelledBookings()).isEqualTo(1);
        assertThat(response.expiredBookings()).isEqualTo(1);
        assertThat(response.averageWaitingSeconds()).isEqualTo(210.0d);
        assertThat(response.averageServiceSeconds()).isEqualTo(900.0d);
        assertThat(response.busiestDepartment()).isEqualTo("Test Department");
        assertThat(response.busiestService()).isEqualTo("Service 1");
        assertThat(response.busiestHour()).isEqualTo(9);
    }

    @Test
    void byDepartmentAggregation() {
        var rows = repository.byDepartment(criteria(false));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).departmentId()).isEqualTo(data.departmentId());
        assertThat(rows.get(0).totalTickets()).isEqualTo(4);
        assertThat(rows.get(0).completedTickets()).isEqualTo(1);
        assertThat(rows.get(0).cancelledTickets()).isEqualTo(1);
        assertThat(rows.get(0).noShowTickets()).isEqualTo(1);
        assertThat(rows.get(0).totalBookings()).isEqualTo(4);
        assertThat(rows.get(0).activeWindowsCount()).isEqualTo(1);
        assertThat(rows.get(0).ticketsPerWindowAverage()).isEqualTo(4.0d);
    }

    @Test
    void byServiceAggregation() {
        var rows = repository.byService(criteria(false));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).serviceId()).isEqualTo(data.serviceId());
        assertThat(rows.get(0).totalTickets()).isEqualTo(4);
        assertThat(rows.get(0).completedTickets()).isEqualTo(1);
        assertThat(rows.get(0).cancelledTickets()).isEqualTo(1);
        assertThat(rows.get(0).noShowTickets()).isEqualTo(1);
        assertThat(rows.get(0).totalBookings()).isEqualTo(4);
        assertThat(rows.get(0).averageWaitingSeconds()).isEqualTo(210.0d);
        assertThat(rows.get(0).averageServiceSeconds()).isEqualTo(900.0d);
    }

    @Test
    void waitingTimeCalculation() {
        WaitingTimeResponse response = repository.waitingTime(criteria(false));

        assertThat(response.averageWaitingSeconds()).isEqualTo(210.0d);
        assertThat(response.medianWaitingSeconds()).isEqualTo(210.0d);
        assertThat(response.minWaitingSeconds()).isEqualTo(120);
        assertThat(response.maxWaitingSeconds()).isEqualTo(300);
        assertThat(response.buckets()).extracting("bucket").containsExactly("0-5 minutes", "5-15 minutes");
    }

    @Test
    void serviceTimeCalculation() {
        ServiceTimeResponse response = repository.serviceTime(criteria(false));

        assertThat(response.averageServiceSeconds()).isEqualTo(900.0d);
        assertThat(response.medianServiceSeconds()).isEqualTo(900.0d);
        assertThat(response.minServiceSeconds()).isEqualTo(900);
        assertThat(response.maxServiceSeconds()).isEqualTo(900);
    }

    @Test
    void bookingReportAggregation() {
        BookingsResponse response = repository.bookings(criteria(false));

        assertThat(response.totalBookings()).isEqualTo(4);
        assertThat(response.confirmed()).isEqualTo(1);
        assertThat(response.checkedIn()).isEqualTo(1);
        assertThat(response.cancelled()).isEqualTo(1);
        assertThat(response.expired()).isEqualTo(1);
        assertThat(response.checkInRate()).isEqualTo(25.0d);
        assertThat(response.cancellationRate()).isEqualTo(25.0d);
        assertThat(response.expirationRate()).isEqualTo(25.0d);
        assertThat(response.bySource()).extracting("name").containsExactly("ADMIN_CREATED");
    }

    @Test
    void detailedTicketReportMasksPersonalDataByDefault() {
        PageResponse<TicketDetailRow> response = repository.ticketDetails(criteria(false));

        assertThat(response.totalElements()).isEqualTo(4);
        assertThat(response.content())
                .filteredOn(row -> "A-001".equals(row.ticketNumber()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.citizenFullName()).isEqualTo("A*** C***");
                    assertThat(row.servedByUser()).isEqualTo("O*** U***");
                });
    }

    @Test
    void exportRequestCreatesReportExportRow() {
        ExportResponse response = exportService.requestExport(
                new ExportRequest(ReportType.SUMMARY, ReportExportFormat.CSV, filter(false)),
                null
        );

        assertThat(response.status()).isEqualTo(ReportExportStatus.COMPLETED);
        assertThat(response.downloadUrl()).contains(response.id().toString());
        assertThat(exportRepository.count()).isEqualTo(1);
    }

    @Test
    void xlsxExportWritesValidFile() {
        ExportResponse response = exportService.requestExport(
                new ExportRequest(ReportType.SUMMARY, ReportExportFormat.XLSX, filter(false)),
                null
        );

        assertThat(response.status()).isEqualTo(ReportExportStatus.COMPLETED);
        assertThat(response.fileName()).endsWith(".xlsx");
        assertThat(response.fileSizeBytes()).isPositive();
    }

    @Test
    void pdfTooLargeReturnsReportTooLargeForPdf() {
        assertThatThrownBy(() -> exportService.requestExport(
                new ExportRequest(ReportType.SUMMARY, ReportExportFormat.PDF, filter(false)),
                null
        ))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("REPORT_TOO_LARGE_FOR_PDF"));
    }

    @Test
    void exportDownloadScopeCheck() {
        UUID exportId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO report_exports (id, report_type, export_format, status, department_id, filters_json)
                VALUES (?, 'SUMMARY', 'CSV', 'COMPLETED', ?, ?::jsonb)
                """, exportId, data.departmentId(), """
                {"dateFrom":"2026-07-01","dateTo":"2026-07-01","departmentId":"%s"}
                """.formatted(data.departmentId()));
        authenticate(UUID.randomUUID(), "REPORT_READ");

        assertThatThrownBy(() -> exportService.getStatus(exportId))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("REPORT_SCOPE_FORBIDDEN"));
    }

    private void seedReportRows() {
        cancellationReasonId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cancellation_reasons (id, code, name, active)
                VALUES (?, 'CLIENT_CANCELLED', 'Client cancelled', true)
                """, cancellationReasonId);
        insertTicket("A-001", "COMPLETED", "09:00:00+00", "09:05:00+00", "09:10:00+00", "09:25:00+00", null, "Alice Citizen", data.userId());
        insertTicket("A-002", "WAITING", "09:30:00+00", null, null, null, null, "Bob Citizen", null);
        insertTicket("A-003", "CANCELLED", "10:00:00+00", null, null, null, cancellationReasonId, "Carol Citizen", null);
        insertTicket("A-004", "NO_SHOW", "10:30:00+00", "10:32:00+00", null, null, null, "Dave Citizen", null);

        insertBooking("B-001", "CHECKED_IN", "09:00", "09:30", "Erin Citizen");
        insertBooking("B-002", "CANCELLED", "10:00", "10:30", "Frank Citizen");
        insertBooking("B-003", "EXPIRED", "11:00", "11:30", "Grace Citizen");
        insertBooking("B-004", "CONFIRMED", "12:00", "12:30", "Helen Citizen");

        jdbcTemplate.update("""
                INSERT INTO integration_requests (client_code, external_request_id, idempotency_key, request_hash, response_body, status, error_code, response_status, created_at, updated_at)
                VALUES
                  ('CRM_MAIN', 'ext-1', 'idem-1', 'hash-1', '{}'::jsonb, 'SUCCEEDED', null, 200, '2026-07-01T09:00:00Z', '2026-07-01T09:00:01Z'),
                  ('CRM_MAIN', 'ext-2', null, 'hash-2', '{}'::jsonb, 'FAILED', 'IDEMPOTENCY_CONFLICT', 409, '2026-07-01T09:10:00Z', '2026-07-01T09:10:01Z')
                """);
    }

    private void insertTicket(
            String number,
            String status,
            String createdTime,
            String calledTime,
            String startedTime,
            String completedTime,
            UUID reasonId,
            String citizenName,
            UUID operatorId
    ) {
        jdbcTemplate.update("""
                INSERT INTO tickets (
                  id, department_id, service_id, service_category_id, ticket_number, ticket_prefix, sequence_number,
                  work_date, region_id, source, status, priority, citizen_full_name, current_window_id, current_operator_id,
                  called_at, service_started_at, service_completed_at, completed_at, cancellation_reason_id, created_at, updated_at
                )
                VALUES (
                  gen_random_uuid(), ?, ?, ?, ?, 'A', ?,
                  ?, ?, 'ADMIN_CREATED', ?, 0, ?, ?, ?,
                  CASE WHEN ?::text IS NULL THEN NULL ELSE (?::date || 'T' || ?)::timestamptz END,
                  CASE WHEN ?::text IS NULL THEN NULL ELSE (?::date || 'T' || ?)::timestamptz END,
                  CASE WHEN ?::text IS NULL THEN NULL ELSE (?::date || 'T' || ?)::timestamptz END,
                  CASE WHEN ?::text IS NULL THEN NULL ELSE (?::date || 'T' || ?)::timestamptz END,
                  ?, (?::date || 'T' || ?)::timestamptz, (?::date || 'T' || ?)::timestamptz
                )
                """,
                data.departmentId(), data.serviceId(), data.categoryId(), number, Integer.parseInt(number.substring(2)),
                REPORT_DAY, data.regionId(), status, citizenName, data.windowId(), operatorId,
                calledTime, REPORT_DAY, calledTime,
                startedTime, REPORT_DAY, startedTime,
                completedTime, REPORT_DAY, completedTime,
                completedTime, REPORT_DAY, completedTime,
                reasonId, REPORT_DAY, createdTime, REPORT_DAY, createdTime);
    }

    private void insertBooking(String number, String status, String start, String end, String citizenName) {
        jdbcTemplate.update("""
                INSERT INTO bookings (
                  id, booking_number, department_id, service_id, source, external_source, status,
                  citizen_full_name, booked_date, starts_at, ends_at, booking_date, booking_start, booking_end, qr_token
                )
                VALUES (gen_random_uuid(), ?, ?, ?, 'ADMIN_CREATED', 'ADMIN_CREATED', ?, ?, ?, ?::time, ?::time, ?, ?::time, ?::time, ?)
                """, number, data.departmentId(), data.serviceId(), status, citizenName, REPORT_DAY, start, end, REPORT_DAY, start, end, number + "-qr");
    }

    private ReportCriteria criteria(boolean includePersonalData) {
        ReportFilter filter = filter(includePersonalData);
        return new ReportCriteria(filter, null, true, includePersonalData, true, data.userId(), 0, 50);
    }

    private ReportFilter filter(boolean includePersonalData) {
        ReportFilter filter = new ReportFilter();
        filter.setDateFrom(REPORT_DAY);
        filter.setDateTo(REPORT_DAY);
        filter.setIncludePersonalData(includePersonalData);
        filter.setPage(0);
        filter.setSize(50);
        return filter;
    }
}
