package kg.equeue.backend.reports;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.reports.ReportDtos.BookingDetailRow;
import kg.equeue.backend.reports.ReportDtos.BookingsResponse;
import kg.equeue.backend.reports.ReportDtos.ByDepartmentRow;
import kg.equeue.backend.reports.ReportDtos.ByEmployeeRow;
import kg.equeue.backend.reports.ReportDtos.ByRegionRow;
import kg.equeue.backend.reports.ReportDtos.ByServiceRow;
import kg.equeue.backend.reports.ReportDtos.BySourceRow;
import kg.equeue.backend.reports.ReportDtos.ByStatusRow;
import kg.equeue.backend.reports.ReportDtos.CancellationsResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportsService {

    private final ReportFilterValidator validator;
    private final ReportQueryRepository repository;
    private final AuditService auditService;
    private final ReportAggregationMapper mapper;

    public ReportsService(ReportFilterValidator validator,
                          ReportQueryRepository repository,
                          AuditService auditService,
                          ReportAggregationMapper mapper) {
        this.validator = validator;
        this.repository = repository;
        this.auditService = auditService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.SUMMARY, false);
        SummaryResponse response = repository.summary(criteria);
        auditViewed(ReportType.SUMMARY, filter, request);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ByRegionRow> byRegion(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.BY_REGION, false);
        List<ByRegionRow> rows = repository.byRegion(criteria);
        auditViewed(ReportType.BY_REGION, filter, request);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ByDepartmentRow> byDepartment(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.BY_DEPARTMENT, false);
        List<ByDepartmentRow> rows = repository.byDepartment(criteria);
        auditViewed(ReportType.BY_DEPARTMENT, filter, request);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ByEmployeeRow> byEmployee(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.BY_EMPLOYEE, false);
        List<ByEmployeeRow> rows = repository.byEmployee(criteria);
        auditViewed(ReportType.BY_EMPLOYEE, filter, request);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ByServiceRow> byService(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.BY_SERVICE, false);
        List<ByServiceRow> rows = repository.byService(criteria);
        auditViewed(ReportType.BY_SERVICE, filter, request);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<BySourceRow> bySource(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.BY_SOURCE, false);
        List<BySourceRow> rows = repository.bySource(criteria);
        auditViewed(ReportType.BY_SOURCE, filter, request);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ByStatusRow> byStatus(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.BY_STATUS, false);
        List<ByStatusRow> rows = repository.byStatus(criteria);
        auditViewed(ReportType.BY_STATUS, filter, request);
        return rows;
    }

    @Transactional(readOnly = true)
    public WaitingTimeResponse waitingTime(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.WAITING_TIME, false);
        WaitingTimeResponse response = repository.waitingTime(criteria);
        auditViewed(ReportType.WAITING_TIME, filter, request);
        return response;
    }

    @Transactional(readOnly = true)
    public ServiceTimeResponse serviceTime(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.SERVICE_TIME, false);
        ServiceTimeResponse response = repository.serviceTime(criteria);
        auditViewed(ReportType.SERVICE_TIME, filter, request);
        return response;
    }

    @Transactional(readOnly = true)
    public CancellationsResponse cancellations(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.CANCELLATIONS, false);
        CancellationsResponse response = repository.cancellations(criteria);
        auditViewed(ReportType.CANCELLATIONS, filter, request);
        return response;
    }

    @Transactional(readOnly = true)
    public NoShowsResponse noShows(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.NO_SHOWS, false);
        NoShowsResponse response = repository.noShows(criteria);
        auditViewed(ReportType.NO_SHOWS, filter, request);
        return response;
    }

    @Transactional(readOnly = true)
    public BookingsResponse bookings(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.BOOKINGS, false);
        BookingsResponse response = repository.bookings(criteria);
        auditViewed(ReportType.BOOKINGS, filter, request);
        return response;
    }

    @Transactional(readOnly = true)
    public List<WindowWorkloadRow> windowWorkload(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.WINDOW_WORKLOAD, false);
        List<WindowWorkloadRow> rows = repository.windowWorkload(criteria);
        auditViewed(ReportType.WINDOW_WORKLOAD, filter, request);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<WorkloadHourlyRow> workloadHourly(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.WORKLOAD_HOURLY, false);
        List<WorkloadHourlyRow> rows = repository.workloadHourly(criteria);
        auditViewed(ReportType.WORKLOAD_HOURLY, filter, request);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<WorkloadDailyRow> workloadDaily(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.WORKLOAD_DAILY, false);
        List<WorkloadDailyRow> rows = repository.workloadDaily(criteria);
        auditViewed(ReportType.WORKLOAD_DAILY, filter, request);
        return rows;
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketDetailRow> ticketDetails(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.TICKETS_DETAIL, true);
        PageResponse<TicketDetailRow> response = repository.ticketDetails(criteria);
        auditViewed(ReportType.TICKETS_DETAIL, filter, request);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingDetailRow> bookingDetails(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.BOOKINGS_DETAIL, true);
        PageResponse<BookingDetailRow> response = repository.bookingDetails(criteria);
        auditViewed(ReportType.BOOKINGS_DETAIL, filter, request);
        return response;
    }

    @Transactional(readOnly = true)
    public List<IntegrationReportRow> integrations(ReportFilter filter, HttpServletRequest request) {
        ReportCriteria criteria = criteria(filter, ReportType.INTEGRATIONS, false);
        List<IntegrationReportRow> rows = repository.integrations(criteria);
        auditViewed(ReportType.INTEGRATIONS, filter, request);
        return rows;
    }

    public ReportCriteria validateForExport(ReportDtos.ExportRequest request) {
        return validator.validate(request.filters(), request.reportType(), isDetailed(request.reportType()), true);
    }

    @Transactional(readOnly = true)
    public ReportTable tableForExport(ReportType reportType, ReportCriteria criteria, int maxRows) {
        ReportCriteria exportCriteria = criteria.withPageAndSize(0, maxRows);
        return switch (reportType) {
            case SUMMARY -> table("Сводный отчет",
                    List.of("Показатель", "Значение"),
                    summaryRows(repository.summary(criteria)));
            case BY_REGION -> table("Отчет по регионам",
                    List.of("Регион", "Отделов", "Талонов", "Завершено", "Отменено", "Неявки", "Бронирований", "Среднее ожидание", "Среднее обслуживание"),
                    rows(repository.byRegion(criteria), row -> row(row.regionName(), row.departmentsCount(), row.totalTickets(), row.completedTickets(), row.cancelledTickets(), row.noShowTickets(), row.totalBookings(), row.averageWaitingSeconds(), row.averageServiceSeconds())));
            case BY_DEPARTMENT -> table("Отчет по отделам",
                    List.of("Отдел", "Регион", "Талонов", "Завершено", "Отменено", "Неявки", "Бронирований", "Окон", "Талонов на окно"),
                    rows(repository.byDepartment(criteria), row -> row(row.departmentName(), row.regionName(), row.totalTickets(), row.completedTickets(), row.cancelledTickets(), row.noShowTickets(), row.totalBookings(), row.activeWindowsCount(), row.ticketsPerWindowAverage())));
            case BY_EMPLOYEE -> table("Отчет по сотрудникам",
                    List.of("Сотрудник", "Отдел", "Обслужено", "Завершено", "Отменено", "Неявки", "Среднее обслуживание", "Первое обслуживание", "Последнее обслуживание"),
                    rows(repository.byEmployee(criteria), row -> row(row.employeeFullName(), row.departmentName(), row.totalServed(), row.completedTickets(), row.cancelledTickets(), row.noShowTickets(), row.averageServiceSeconds(), row.firstServiceAt(), row.lastServiceAt())));
            case BY_SERVICE -> table("Отчет по услугам",
                    List.of("Услуга", "Категория", "Талонов", "Завершено", "Отменено", "Неявки", "Бронирований", "Среднее ожидание", "Среднее обслуживание"),
                    rows(repository.byService(criteria), row -> row(row.serviceNameRu(), row.categoryName(), row.totalTickets(), row.completedTickets(), row.cancelledTickets(), row.noShowTickets(), row.totalBookings(), row.averageWaitingSeconds(), row.averageServiceSeconds())));
            case BY_SOURCE -> table("Отчет по источникам",
                    List.of("Источник", "Талонов", "Бронирований", "Завершено", "Отменено", "Конверсия в check-in"),
                    rows(repository.bySource(criteria), row -> row(row.source(), row.totalTickets(), row.totalBookings(), row.completedTickets(), row.cancelledTickets(), row.conversionToCheckInPercent())));
            case BY_STATUS -> table("Отчет по статусам",
                    List.of("Статус", "Количество", "Процент"),
                    rows(repository.byStatus(criteria), row -> row(row.status(), row.count(), row.percentage())));
            case WAITING_TIME -> table("Время ожидания", List.of("Показатель", "Значение"), waitingRows(repository.waitingTime(criteria)));
            case SERVICE_TIME -> table("Время обслуживания", List.of("Показатель", "Значение"), serviceRows(repository.serviceTime(criteria)));
            case CANCELLATIONS -> table("Отмены",
                    List.of("Причина", "Количество", "Процент"),
                    rows(repository.cancellations(criteria).reasons(), row -> row(row.cancellationReasonName(), row.count(), row.percentage())));
            case NO_SHOWS -> table("Неявки", List.of("Показатель", "Значение"), noShowRows(repository.noShows(criteria)));
            case BOOKINGS -> table("Бронирования", List.of("Показатель", "Значение"), bookingRows(repository.bookings(criteria)));
            case WINDOW_WORKLOAD -> table("Нагрузка окон",
                    List.of("Окно", "Отдел", "Вызвано", "Завершено", "Среднее обслуживание", "Активное время"),
                    rows(repository.windowWorkload(criteria), row -> row(row.windowNumber(), row.departmentName(), row.totalCalled(), row.totalCompleted(), row.averageServiceSeconds(), row.activeServiceSeconds())));
            case WORKLOAD_HOURLY -> table("Нагрузка по часам",
                    List.of("Дата", "Час", "Отдел", "Талоны", "Бронирования", "Завершено", "Среднее ожидание", "Среднее обслуживание"),
                    rows(repository.workloadHourly(criteria), row -> row(row.date(), row.hour(), row.departmentId(), row.totalTickets(), row.totalBookings(), row.completedTickets(), row.averageWaitingSeconds(), row.averageServiceSeconds())));
            case WORKLOAD_DAILY -> table("Нагрузка по дням",
                    List.of("Дата", "Отдел", "Талоны", "Бронирования", "Завершено", "Отменено", "Неявки", "Среднее ожидание", "Среднее обслуживание"),
                    rows(repository.workloadDaily(criteria), row -> row(row.date(), row.departmentId(), row.totalTickets(), row.totalBookings(), row.completedTickets(), row.cancelledTickets(), row.noShowTickets(), row.averageWaitingSeconds(), row.averageServiceSeconds())));
            case TICKETS_DETAIL -> table("Детальный отчет по талонам",
                    List.of("Номер", "Отдел", "Окно", "Услуга", "Источник", "Статус", "Создан", "Вызван", "Начато", "Завершено", "Ожидание", "Обслуживание", "Сотрудник", "Гражданин"),
                    rows(repository.ticketDetails(exportCriteria).content(), row -> row(row.ticketNumber(), row.department(), row.window(), row.service(), row.source(), row.status(), row.createdAt(), row.calledAt(), row.serviceStartedAt(), row.serviceCompletedAt(), row.waitingSeconds(), row.serviceSeconds(), row.servedByUser(), row.citizenFullName())));
            case BOOKINGS_DETAIL -> table("Детальный отчет по бронированиям",
                    List.of("Номер", "Источник", "External ID", "Отдел", "Услуга", "Дата", "Начало", "Конец", "Статус", "Талон", "Гражданин"),
                    rows(repository.bookingDetails(exportCriteria).content(), row -> row(row.bookingNumber(), row.source(), row.externalId(), row.department(), row.service(), row.bookingDate(), row.bookingStart(), row.bookingEnd(), row.status(), row.ticketNumber(), row.citizenFullName())));
            case INTEGRATIONS -> table("Интеграции",
                    List.of("Клиент", "Запросов", "Успешно", "Ошибок", "Идемпотентных", "Конфликтов", "Среднее время", "Последний запрос"),
                    rows(repository.integrations(criteria), row -> row(row.clientCode(), row.totalRequests(), row.successfulRequests(), row.failedRequests(), row.duplicateIdempotentRequests(), row.idempotencyConflicts(), row.averageResponseTimeMs(), row.lastRequestAt())));
        };
    }

    private ReportCriteria criteria(ReportFilter filter, ReportType type, boolean detailed) {
        return validator.validate(filter, type, detailed, false);
    }

    private void auditViewed(ReportType reportType, ReportFilter filter, HttpServletRequest request) {
        auditService.write("REPORT_VIEWED", "REPORT", null, mapper.auditJson(reportType, filter), request);
    }

    private boolean isDetailed(ReportType reportType) {
        return reportType == ReportType.TICKETS_DETAIL || reportType == ReportType.BOOKINGS_DETAIL;
    }

    private ReportTable table(String title, List<String> headers, List<List<Object>> rows) {
        return new ReportTable(title, headers, rows);
    }

    private <T> List<List<Object>> rows(List<T> source, Function<T, List<Object>> mapper) {
        return source.stream().map(mapper).toList();
    }

    private List<Object> row(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private List<List<Object>> summaryRows(SummaryResponse summary) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(row("Всего талонов", summary.totalTickets()));
        rows.add(row("Ожидают", summary.waitingTickets()));
        rows.add(row("Завершено", summary.completedTickets()));
        rows.add(row("Отменено", summary.cancelledTickets()));
        rows.add(row("Неявки", summary.noShowTickets()));
        rows.add(row("Истекшие", summary.expiredTickets()));
        rows.add(row("Всего бронирований", summary.totalBookings()));
        rows.add(row("Check-in бронирований", summary.completedBookings()));
        rows.add(row("Отменено бронирований", summary.cancelledBookings()));
        rows.add(row("Истекшие бронирования", summary.expiredBookings()));
        rows.add(row("Среднее ожидание", summary.averageWaitingSeconds()));
        rows.add(row("Среднее обслуживание", summary.averageServiceSeconds()));
        rows.add(row("Талонов в день", summary.averageTicketsPerDay()));
        rows.add(row("Самый загруженный отдел", summary.busiestDepartment()));
        rows.add(row("Самая загруженная услуга", summary.busiestService()));
        rows.add(row("Самый загруженный час", summary.busiestHour()));
        return rows;
    }

    private List<List<Object>> waitingRows(WaitingTimeResponse response) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(row("Среднее", response.averageWaitingSeconds()));
        rows.add(row("Медиана", response.medianWaitingSeconds()));
        rows.add(row("Минимум", response.minWaitingSeconds()));
        rows.add(row("Максимум", response.maxWaitingSeconds()));
        rows.add(row("P90", response.p90WaitingSeconds()));
        response.buckets().forEach(bucket -> rows.add(row(bucket.bucket(), bucket.count())));
        return rows;
    }

    private List<List<Object>> serviceRows(ServiceTimeResponse response) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(row("Среднее", response.averageServiceSeconds()));
        rows.add(row("Медиана", response.medianServiceSeconds()));
        rows.add(row("Минимум", response.minServiceSeconds()));
        rows.add(row("Максимум", response.maxServiceSeconds()));
        rows.add(row("P90", response.p90ServiceSeconds()));
        response.buckets().forEach(bucket -> rows.add(row(bucket.bucket(), bucket.count())));
        return rows;
    }

    private List<List<Object>> noShowRows(NoShowsResponse response) {
        return List.of(
                row("Всего неявок", response.totalNoShows()),
                row("Процент неявок", response.noShowRate())
        );
    }

    private List<List<Object>> bookingRows(BookingsResponse response) {
        return List.of(
                row("Всего бронирований", response.totalBookings()),
                row("Подтверждено", response.confirmed()),
                row("Check-in", response.checkedIn()),
                row("Отменено", response.cancelled()),
                row("Истекло", response.expired()),
                row("Неявки", response.noShow()),
                row("Check-in rate", response.checkInRate()),
                row("Cancellation rate", response.cancellationRate()),
                row("Expiration rate", response.expirationRate())
        );
    }
}
