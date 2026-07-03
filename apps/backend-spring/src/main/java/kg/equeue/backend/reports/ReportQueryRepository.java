package kg.equeue.backend.reports;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kg.equeue.backend.reports.ReportDtos.BookingDetailRow;
import kg.equeue.backend.reports.ReportDtos.BookingsResponse;
import kg.equeue.backend.reports.ReportDtos.ByDepartmentRow;
import kg.equeue.backend.reports.ReportDtos.ByEmployeeRow;
import kg.equeue.backend.reports.ReportDtos.ByRegionRow;
import kg.equeue.backend.reports.ReportDtos.ByServiceRow;
import kg.equeue.backend.reports.ReportDtos.BySourceRow;
import kg.equeue.backend.reports.ReportDtos.ByStatusRow;
import kg.equeue.backend.reports.ReportDtos.CancellationRow;
import kg.equeue.backend.reports.ReportDtos.CancellationsResponse;
import kg.equeue.backend.reports.ReportDtos.HourMetricRow;
import kg.equeue.backend.reports.ReportDtos.IntegrationReportRow;
import kg.equeue.backend.reports.ReportDtos.NoShowsResponse;
import kg.equeue.backend.reports.ReportDtos.PageResponse;
import kg.equeue.backend.reports.ReportDtos.ServiceTimeResponse;
import kg.equeue.backend.reports.ReportDtos.SimpleMetricRow;
import kg.equeue.backend.reports.ReportDtos.SummaryResponse;
import kg.equeue.backend.reports.ReportDtos.TicketDetailRow;
import kg.equeue.backend.reports.ReportDtos.TimeBucketRow;
import kg.equeue.backend.reports.ReportDtos.WaitingTimeResponse;
import kg.equeue.backend.reports.ReportDtos.WindowWorkloadRow;
import kg.equeue.backend.reports.ReportDtos.WorkloadDailyRow;
import kg.equeue.backend.reports.ReportDtos.WorkloadHourlyRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ReportAggregationMapper mapper;

    public ReportQueryRepository(NamedParameterJdbcTemplate jdbcTemplate, ReportAggregationMapper mapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapper = mapper;
    }

    public SummaryResponse summary(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        Map<String, Object> tickets = jdbcTemplate.queryForMap("""
                SELECT
                  COUNT(*)::bigint AS total_tickets,
                  COUNT(*) FILTER (WHERE t.status = 'WAITING')::bigint AS waiting_tickets,
                  COUNT(*) FILTER (WHERE t.status = 'COMPLETED')::bigint AS completed_tickets,
                  COUNT(*) FILTER (WHERE t.status = 'CANCELLED')::bigint AS cancelled_tickets,
                  COUNT(*) FILTER (WHERE t.status = 'NO_SHOW')::bigint AS no_show_tickets,
                  COUNT(*) FILTER (WHERE t.status = 'EXPIRED')::bigint AS expired_tickets,
                  AVG(EXTRACT(EPOCH FROM (t.called_at - t.created_at))) FILTER (WHERE t.called_at IS NOT NULL) AS average_waiting_seconds,
                  AVG(EXTRACT(EPOCH FROM (COALESCE(t.service_completed_at, t.completed_at) - t.service_started_at)))
                    FILTER (WHERE t.service_started_at IS NOT NULL AND COALESCE(t.service_completed_at, t.completed_at) IS NOT NULL) AS average_service_seconds
                FROM tickets t
                JOIN departments d ON d.id = t.department_id
                JOIN services s ON s.id = t.service_id
                WHERE """ + ticketWhere("t", "d", "s", criteria), params);

        Map<String, Object> bookings = jdbcTemplate.queryForMap("""
                SELECT
                  COUNT(*)::bigint AS total_bookings,
                  COUNT(*) FILTER (WHERE b.status = 'CHECKED_IN')::bigint AS completed_bookings,
                  COUNT(*) FILTER (WHERE b.status = 'CANCELLED')::bigint AS cancelled_bookings,
                  COUNT(*) FILTER (WHERE b.status = 'EXPIRED')::bigint AS expired_bookings
                FROM bookings b
                JOIN departments d ON d.id = b.department_id
                JOIN services s ON s.id = b.service_id
                WHERE """ + bookingWhere("b", "d", "s", criteria), params);

        long totalTickets = mapper.longValue(tickets, "total_tickets");
        long days = ChronoUnit.DAYS.between(criteria.filter().getDateFrom(), criteria.filter().getDateTo()) + 1;
        return new SummaryResponse(
                totalTickets,
                mapper.longValue(tickets, "waiting_tickets"),
                mapper.longValue(tickets, "completed_tickets"),
                mapper.longValue(tickets, "cancelled_tickets"),
                mapper.longValue(tickets, "no_show_tickets"),
                mapper.longValue(tickets, "expired_tickets"),
                mapper.longValue(bookings, "total_bookings"),
                mapper.longValue(bookings, "completed_bookings"),
                mapper.longValue(bookings, "cancelled_bookings"),
                mapper.longValue(bookings, "expired_bookings"),
                mapper.doubleValue(tickets, "average_waiting_seconds"),
                mapper.doubleValue(tickets, "average_service_seconds"),
                days <= 0 ? 0.0d : totalTickets * 1.0d / days,
                firstString("""
                        SELECT d.name
                        FROM tickets t
                        JOIN departments d ON d.id = t.department_id
                        JOIN services s ON s.id = t.service_id
                        WHERE """ + ticketWhere("t", "d", "s", criteria) + """
                        GROUP BY d.name
                        ORDER BY COUNT(*) DESC, d.name
                        LIMIT 1
                        """, params, "name"),
                firstString("""
                        SELECT s.name
                        FROM tickets t
                        JOIN departments d ON d.id = t.department_id
                        JOIN services s ON s.id = t.service_id
                        WHERE """ + ticketWhere("t", "d", "s", criteria) + """
                        GROUP BY s.name
                        ORDER BY COUNT(*) DESC, s.name
                        LIMIT 1
                        """, params, "name"),
                firstInteger("""
                        SELECT EXTRACT(HOUR FROM t.created_at)::int AS hour
                        FROM tickets t
                        JOIN departments d ON d.id = t.department_id
                        JOIN services s ON s.id = t.service_id
                        WHERE """ + ticketWhere("t", "d", "s", criteria) + """
                        GROUP BY hour
                        ORDER BY COUNT(*) DESC, hour
                        LIMIT 1
                        """, params, "hour")
        );
    }

    public List<ByRegionRow> byRegion(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        String ticketWhere = ticketWhere("t", "d", "s", criteria);
        String bookingWhere = bookingWhere("b", "d", "s", criteria);
        String sql = """
                SELECT
                  r.id AS region_id,
                  r.name AS region_name,
                """ +
                "  (SELECT COUNT(DISTINCT d0.id)::bigint FROM departments d0 WHERE d0.region_id = r.id" + departmentScopeSql("d0", criteria) + ") AS departments_count,\n" +
                "  (SELECT COUNT(*)::bigint FROM tickets t JOIN departments d ON d.id = t.department_id JOIN services s ON s.id = t.service_id WHERE d.region_id = r.id AND " + ticketWhere + ") AS total_tickets,\n" +
                "  (SELECT COUNT(*)::bigint FROM tickets t JOIN departments d ON d.id = t.department_id JOIN services s ON s.id = t.service_id WHERE d.region_id = r.id AND t.status = 'COMPLETED' AND " + ticketWhere + ") AS completed_tickets,\n" +
                "  (SELECT COUNT(*)::bigint FROM tickets t JOIN departments d ON d.id = t.department_id JOIN services s ON s.id = t.service_id WHERE d.region_id = r.id AND t.status = 'CANCELLED' AND " + ticketWhere + ") AS cancelled_tickets,\n" +
                "  (SELECT COUNT(*)::bigint FROM tickets t JOIN departments d ON d.id = t.department_id JOIN services s ON s.id = t.service_id WHERE d.region_id = r.id AND t.status = 'NO_SHOW' AND " + ticketWhere + ") AS no_show_tickets,\n" +
                "  (SELECT COUNT(*)::bigint FROM bookings b JOIN departments d ON d.id = b.department_id JOIN services s ON s.id = b.service_id WHERE d.region_id = r.id AND " + bookingWhere + ") AS total_bookings,\n" +
                "  (SELECT AVG(EXTRACT(EPOCH FROM (t.called_at - t.created_at))) FROM tickets t JOIN departments d ON d.id = t.department_id JOIN services s ON s.id = t.service_id WHERE d.region_id = r.id AND t.called_at IS NOT NULL AND " + ticketWhere + ") AS average_waiting_seconds,\n" +
                "  (SELECT AVG(EXTRACT(EPOCH FROM (COALESCE(t.service_completed_at, t.completed_at) - t.service_started_at))) FROM tickets t JOIN departments d ON d.id = t.department_id JOIN services s ON s.id = t.service_id WHERE d.region_id = r.id AND t.service_started_at IS NOT NULL AND COALESCE(t.service_completed_at, t.completed_at) IS NOT NULL AND " + ticketWhere + ") AS average_service_seconds\n" +
                """
                FROM regions r
                WHERE (:regionId::uuid IS NULL OR r.id = :regionId)
                """ + regionScopeSql(criteria) + """
                ORDER BY r.name
                """;
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new ByRegionRow(
                mapper.uuid(rs, "region_id"),
                rs.getString("region_name"),
                mapper.longValue(rs, "departments_count"),
                mapper.longValue(rs, "total_tickets"),
                mapper.longValue(rs, "completed_tickets"),
                mapper.longValue(rs, "cancelled_tickets"),
                mapper.longValue(rs, "no_show_tickets"),
                mapper.longValue(rs, "total_bookings"),
                mapper.doubleValue(rs, "average_waiting_seconds"),
                mapper.doubleValue(rs, "average_service_seconds")
        ));
    }

    public List<ByDepartmentRow> byDepartment(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        String ticketWhere = ticketWhereWithoutDepartment("t", "s", criteria);
        String bookingWhere = bookingWhereWithoutDepartment("b", "s", criteria);
        String sql = """
                SELECT
                  d.id AS department_id,
                  d.name AS department_name,
                  r.name AS region_name,
                """ +
                "  (SELECT COUNT(*)::bigint FROM tickets t JOIN services s ON s.id = t.service_id WHERE t.department_id = d.id AND " + ticketWhere + ") AS total_tickets,\n" +
                "  (SELECT COUNT(*)::bigint FROM tickets t JOIN services s ON s.id = t.service_id WHERE t.department_id = d.id AND t.status = 'COMPLETED' AND " + ticketWhere + ") AS completed_tickets,\n" +
                "  (SELECT COUNT(*)::bigint FROM tickets t JOIN services s ON s.id = t.service_id WHERE t.department_id = d.id AND t.status = 'CANCELLED' AND " + ticketWhere + ") AS cancelled_tickets,\n" +
                "  (SELECT COUNT(*)::bigint FROM tickets t JOIN services s ON s.id = t.service_id WHERE t.department_id = d.id AND t.status = 'NO_SHOW' AND " + ticketWhere + ") AS no_show_tickets,\n" +
                "  (SELECT COUNT(*)::bigint FROM bookings b JOIN services s ON s.id = b.service_id WHERE b.department_id = d.id AND " + bookingWhere + ") AS total_bookings,\n" +
                "  (SELECT COUNT(*)::bigint FROM bookings b JOIN services s ON s.id = b.service_id WHERE b.department_id = d.id AND b.status = 'CHECKED_IN' AND " + bookingWhere + ") AS checked_in_bookings,\n" +
                "  (SELECT AVG(EXTRACT(EPOCH FROM (t.called_at - t.created_at))) FROM tickets t JOIN services s ON s.id = t.service_id WHERE t.department_id = d.id AND t.called_at IS NOT NULL AND " + ticketWhere + ") AS average_waiting_seconds,\n" +
                "  (SELECT AVG(EXTRACT(EPOCH FROM (COALESCE(t.service_completed_at, t.completed_at) - t.service_started_at))) FROM tickets t JOIN services s ON s.id = t.service_id WHERE t.department_id = d.id AND t.service_started_at IS NOT NULL AND COALESCE(t.service_completed_at, t.completed_at) IS NOT NULL AND " + ticketWhere + ") AS average_service_seconds,\n" +
                "  (SELECT COUNT(*)::bigint FROM service_windows sw WHERE sw.department_id = d.id AND sw.active = true) AS active_windows_count\n" +
                """
                FROM departments d
                JOIN regions r ON r.id = d.region_id
                WHERE """ + departmentWhere("d", criteria) + """
                ORDER BY d.name
                """;
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            long totalTickets = mapper.longValue(rs, "total_tickets");
            long activeWindows = mapper.longValue(rs, "active_windows_count");
            return new ByDepartmentRow(
                    mapper.uuid(rs, "department_id"),
                    rs.getString("department_name"),
                    rs.getString("region_name"),
                    totalTickets,
                    mapper.longValue(rs, "completed_tickets"),
                    mapper.longValue(rs, "cancelled_tickets"),
                    mapper.longValue(rs, "no_show_tickets"),
                    mapper.longValue(rs, "total_bookings"),
                    mapper.longValue(rs, "checked_in_bookings"),
                    mapper.doubleValue(rs, "average_waiting_seconds"),
                    mapper.doubleValue(rs, "average_service_seconds"),
                    activeWindows,
                    activeWindows == 0 ? 0.0d : totalTickets * 1.0d / activeWindows
            );
        });
    }

    public List<ByEmployeeRow> byEmployee(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        return jdbcTemplate.query("""
                SELECT
                  u.id AS employee_id,
                  u.full_name AS employee_full_name,
                  t.department_id,
                  d.name AS department_name,
                  COUNT(*)::bigint AS total_served,
                  COUNT(*) FILTER (WHERE t.status = 'COMPLETED')::bigint AS completed_tickets,
                  COUNT(*) FILTER (WHERE t.status = 'CANCELLED')::bigint AS cancelled_tickets,
                  COUNT(*) FILTER (WHERE t.status = 'NO_SHOW')::bigint AS no_show_tickets,
                  AVG(EXTRACT(EPOCH FROM (COALESCE(t.service_completed_at, t.completed_at) - t.service_started_at)))
                    FILTER (WHERE t.service_started_at IS NOT NULL AND COALESCE(t.service_completed_at, t.completed_at) IS NOT NULL) AS average_service_seconds,
                  MIN(t.service_started_at) AS first_service_at,
                  MAX(COALESCE(t.service_completed_at, t.completed_at, t.updated_at)) AS last_service_at
                FROM tickets t
                JOIN departments d ON d.id = t.department_id
                JOIN services s ON s.id = t.service_id
                JOIN users u ON u.id = t.current_operator_id
                WHERE t.current_operator_id IS NOT NULL AND """ + ticketWhere("t", "d", "s", criteria) + """
                GROUP BY u.id, u.full_name, t.department_id, d.name
                ORDER BY d.name, u.full_name
                """, params, (rs, rowNum) -> new ByEmployeeRow(
                mapper.uuid(rs, "employee_id"),
                mapper.personalName(rs.getString("employee_full_name"), criteria.includePersonalData()),
                mapper.uuid(rs, "department_id"),
                rs.getString("department_name"),
                mapper.longValue(rs, "total_served"),
                mapper.longValue(rs, "completed_tickets"),
                mapper.longValue(rs, "cancelled_tickets"),
                mapper.longValue(rs, "no_show_tickets"),
                mapper.doubleValue(rs, "average_service_seconds"),
                mapper.instant(rs, "first_service_at"),
                mapper.instant(rs, "last_service_at")
        ));
    }

    public List<ByServiceRow> byService(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        String ticketWhere = ticketWhereWithKnownService("t", "d", criteria);
        String bookingWhere = bookingWhereWithKnownService("b", "d", criteria);
        String sql = """
                SELECT
                  s.id AS service_id,
                  s.name AS service_name,
                  sc.id AS category_id,
                  sc.name AS category_name,
                """ +
                "  (SELECT COUNT(*)::bigint FROM tickets t JOIN departments d ON d.id = t.department_id WHERE t.service_id = s.id AND " + ticketWhere + ") AS total_tickets,\n" +
                "  (SELECT COUNT(*)::bigint FROM tickets t JOIN departments d ON d.id = t.department_id WHERE t.service_id = s.id AND t.status = 'COMPLETED' AND " + ticketWhere + ") AS completed_tickets,\n" +
                "  (SELECT COUNT(*)::bigint FROM tickets t JOIN departments d ON d.id = t.department_id WHERE t.service_id = s.id AND t.status = 'CANCELLED' AND " + ticketWhere + ") AS cancelled_tickets,\n" +
                "  (SELECT COUNT(*)::bigint FROM tickets t JOIN departments d ON d.id = t.department_id WHERE t.service_id = s.id AND t.status = 'NO_SHOW' AND " + ticketWhere + ") AS no_show_tickets,\n" +
                "  (SELECT COUNT(*)::bigint FROM bookings b JOIN departments d ON d.id = b.department_id WHERE b.service_id = s.id AND " + bookingWhere + ") AS total_bookings,\n" +
                "  (SELECT AVG(EXTRACT(EPOCH FROM (t.called_at - t.created_at))) FROM tickets t JOIN departments d ON d.id = t.department_id WHERE t.service_id = s.id AND t.called_at IS NOT NULL AND " + ticketWhere + ") AS average_waiting_seconds,\n" +
                "  (SELECT AVG(EXTRACT(EPOCH FROM (COALESCE(t.service_completed_at, t.completed_at) - t.service_started_at))) FROM tickets t JOIN departments d ON d.id = t.department_id WHERE t.service_id = s.id AND t.service_started_at IS NOT NULL AND COALESCE(t.service_completed_at, t.completed_at) IS NOT NULL AND " + ticketWhere + ") AS average_service_seconds\n" +
                """
                FROM services s
                JOIN service_categories sc ON sc.id = s.category_id
                WHERE (:serviceId::uuid IS NULL OR s.id = :serviceId)
                  AND (:serviceCategoryId::uuid IS NULL OR sc.id = :serviceCategoryId)
                ORDER BY sc.name, s.name
                """;
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new ByServiceRow(
                mapper.uuid(rs, "service_id"),
                rs.getString("service_name"),
                rs.getString("service_name"),
                mapper.uuid(rs, "category_id"),
                rs.getString("category_name"),
                mapper.longValue(rs, "total_tickets"),
                mapper.longValue(rs, "completed_tickets"),
                mapper.longValue(rs, "cancelled_tickets"),
                mapper.longValue(rs, "no_show_tickets"),
                mapper.longValue(rs, "total_bookings"),
                mapper.doubleValue(rs, "average_waiting_seconds"),
                mapper.doubleValue(rs, "average_service_seconds")
        ));
    }

    public List<BySourceRow> bySource(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        return jdbcTemplate.query("""
                WITH ticket_stats AS (
                  SELECT t.source,
                         COUNT(*)::bigint AS total_tickets,
                         COUNT(*) FILTER (WHERE t.status = 'COMPLETED')::bigint AS completed_tickets,
                         COUNT(*) FILTER (WHERE t.status = 'CANCELLED')::bigint AS cancelled_tickets
                  FROM tickets t
                  JOIN departments d ON d.id = t.department_id
                  JOIN services s ON s.id = t.service_id
                  WHERE """ + ticketWhere("t", "d", "s", criteria) + """
                  GROUP BY t.source
                ),
                booking_stats AS (
                  SELECT COALESCE(b.external_source, b.source) AS source,
                         COUNT(*)::bigint AS total_bookings,
                         COUNT(*) FILTER (WHERE b.status = 'CHECKED_IN')::bigint AS checked_in_bookings
                  FROM bookings b
                  JOIN departments d ON d.id = b.department_id
                  JOIN services s ON s.id = b.service_id
                  WHERE """ + bookingWhere("b", "d", "s", criteria) + """
                  GROUP BY COALESCE(b.external_source, b.source)
                )
                SELECT COALESCE(ts.source, bs.source) AS source,
                       COALESCE(ts.total_tickets, 0)::bigint AS total_tickets,
                       COALESCE(bs.total_bookings, 0)::bigint AS total_bookings,
                       COALESCE(ts.completed_tickets, 0)::bigint AS completed_tickets,
                       COALESCE(ts.cancelled_tickets, 0)::bigint AS cancelled_tickets,
                       COALESCE(bs.checked_in_bookings, 0)::bigint AS checked_in_bookings
                FROM ticket_stats ts
                FULL OUTER JOIN booking_stats bs ON bs.source = ts.source
                ORDER BY source
                """, params, (rs, rowNum) -> {
            long totalBookings = mapper.longValue(rs, "total_bookings");
            long checkedIn = mapper.longValue(rs, "checked_in_bookings");
            return new BySourceRow(
                    rs.getString("source"),
                    mapper.longValue(rs, "total_tickets"),
                    totalBookings,
                    mapper.longValue(rs, "completed_tickets"),
                    mapper.longValue(rs, "cancelled_tickets"),
                    mapper.percent(checkedIn, totalBookings)
            );
        });
    }

    public List<ByStatusRow> byStatus(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        List<ByStatusRow> rows = jdbcTemplate.query("""
                WITH stats AS (
                  SELECT t.status, COUNT(*)::bigint AS count
                  FROM tickets t
                  JOIN departments d ON d.id = t.department_id
                  JOIN services s ON s.id = t.service_id
                  WHERE """ + ticketWhere("t", "d", "s", criteria) + """
                  GROUP BY t.status
                ),
                totals AS (SELECT COALESCE(SUM(count), 0)::bigint AS total FROM stats)
                SELECT s.status, s.count, CASE WHEN t.total = 0 THEN 0 ELSE s.count * 100.0 / t.total END AS percentage
                FROM stats s
                CROSS JOIN totals t
                ORDER BY s.status
                """, params, (rs, rowNum) -> new ByStatusRow(
                rs.getString("status"),
                mapper.longValue(rs, "count"),
                mapper.doubleValue(rs, "percentage")
        ));
        return rows;
    }

    public WaitingTimeResponse waitingTime(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        Map<String, Object> stats = jdbcTemplate.queryForMap(durationStatsSql("t.called_at", "t.created_at", criteria), params);
        return new WaitingTimeResponse(
                mapper.doubleValue(stats, "average_seconds"),
                mapper.doubleValue(stats, "median_seconds"),
                nullableLong(stats, "min_seconds"),
                nullableLong(stats, "max_seconds"),
                mapper.doubleValue(stats, "p90_seconds"),
                durationBuckets("t.called_at", "t.created_at", criteria)
        );
    }

    public ServiceTimeResponse serviceTime(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        Map<String, Object> stats = jdbcTemplate.queryForMap(durationStatsSql("COALESCE(t.service_completed_at, t.completed_at)", "t.service_started_at", criteria), params);
        return new ServiceTimeResponse(
                mapper.doubleValue(stats, "average_seconds"),
                mapper.doubleValue(stats, "median_seconds"),
                nullableLong(stats, "min_seconds"),
                nullableLong(stats, "max_seconds"),
                mapper.doubleValue(stats, "p90_seconds"),
                durationBuckets("COALESCE(t.service_completed_at, t.completed_at)", "t.service_started_at", criteria)
        );
    }

    public CancellationsResponse cancellations(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        long total = count("""
                SELECT COUNT(*)::bigint
                FROM tickets t
                JOIN departments d ON d.id = t.department_id
                JOIN services s ON s.id = t.service_id
                WHERE t.status = 'CANCELLED' AND """ + ticketWhere("t", "d", "s", criteria), params);
        List<CancellationRow> rows = jdbcTemplate.query("""
                SELECT cr.id AS cancellation_reason_id,
                       COALESCE(cr.name, 'Без причины') AS cancellation_reason_name,
                       COUNT(*)::bigint AS count
                FROM tickets t
                JOIN departments d ON d.id = t.department_id
                JOIN services s ON s.id = t.service_id
                LEFT JOIN cancellation_reasons cr ON cr.id = t.cancellation_reason_id
                WHERE t.status = 'CANCELLED' AND """ + ticketWhere("t", "d", "s", criteria) + """
                GROUP BY cr.id, cr.name
                ORDER BY count DESC, cancellation_reason_name
                """, params, (rs, rowNum) -> {
            UUID reasonId = mapper.uuid(rs, "cancellation_reason_id");
            long count = mapper.longValue(rs, "count");
            return new CancellationRow(
                    reasonId,
                    rs.getString("cancellation_reason_name"),
                    count,
                    mapper.percent(count, total),
                    "department".equalsIgnoreCase(criteria.filter().getGroupBy()) ? cancellationDepartments(reasonId, criteria) : List.of()
            );
        });
        return new CancellationsResponse(rows);
    }

    public NoShowsResponse noShows(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        long total = count("""
                SELECT COUNT(*)::bigint
                FROM tickets t
                JOIN departments d ON d.id = t.department_id
                JOIN services s ON s.id = t.service_id
                WHERE """ + ticketWhere("t", "d", "s", criteria), params);
        long noShows = count("""
                SELECT COUNT(*)::bigint
                FROM tickets t
                JOIN departments d ON d.id = t.department_id
                JOIN services s ON s.id = t.service_id
                WHERE t.status = 'NO_SHOW' AND """ + ticketWhere("t", "d", "s", criteria), params);
        return new NoShowsResponse(
                noShows,
                simpleMetric("""
                        SELECT d.id, d.name, COUNT(*)::bigint AS count
                        FROM tickets t
                        JOIN departments d ON d.id = t.department_id
                        JOIN services s ON s.id = t.service_id
                        WHERE t.status = 'NO_SHOW' AND """ + ticketWhere("t", "d", "s", criteria) + """
                        GROUP BY d.id, d.name
                        ORDER BY count DESC, d.name
                        """, params),
                simpleMetric("""
                        SELECT s.id, s.name, COUNT(*)::bigint AS count
                        FROM tickets t
                        JOIN departments d ON d.id = t.department_id
                        JOIN services s ON s.id = t.service_id
                        WHERE t.status = 'NO_SHOW' AND """ + ticketWhere("t", "d", "s", criteria) + """
                        GROUP BY s.id, s.name
                        ORDER BY count DESC, s.name
                        """, params),
                hourMetric("""
                        SELECT EXTRACT(HOUR FROM t.created_at)::int AS hour, COUNT(*)::bigint AS count
                        FROM tickets t
                        JOIN departments d ON d.id = t.department_id
                        JOIN services s ON s.id = t.service_id
                        WHERE t.status = 'NO_SHOW' AND """ + ticketWhere("t", "d", "s", criteria) + """
                        GROUP BY hour
                        ORDER BY hour
                        """, params),
                mapper.percent(noShows, total)
        );
    }

    public BookingsResponse bookings(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        Map<String, Object> stats = jdbcTemplate.queryForMap("""
                SELECT
                  COUNT(*)::bigint AS total_bookings,
                  COUNT(*) FILTER (WHERE b.status = 'CONFIRMED')::bigint AS confirmed,
                  COUNT(*) FILTER (WHERE b.status = 'CHECKED_IN')::bigint AS checked_in,
                  COUNT(*) FILTER (WHERE b.status = 'CANCELLED')::bigint AS cancelled,
                  COUNT(*) FILTER (WHERE b.status = 'EXPIRED')::bigint AS expired,
                  COUNT(*) FILTER (WHERE b.status = 'NO_SHOW')::bigint AS no_show
                FROM bookings b
                JOIN departments d ON d.id = b.department_id
                JOIN services s ON s.id = b.service_id
                WHERE """ + bookingWhere("b", "d", "s", criteria), params);
        long total = mapper.longValue(stats, "total_bookings");
        long checkedIn = mapper.longValue(stats, "checked_in");
        long cancelled = mapper.longValue(stats, "cancelled");
        long expired = mapper.longValue(stats, "expired");
        return new BookingsResponse(
                total,
                mapper.longValue(stats, "confirmed"),
                checkedIn,
                cancelled,
                expired,
                mapper.longValue(stats, "no_show"),
                mapper.percent(checkedIn, total),
                mapper.percent(cancelled, total),
                mapper.percent(expired, total),
                simpleMetric("""
                        SELECT NULL::uuid AS id, COALESCE(b.external_source, b.source) AS name, COUNT(*)::bigint AS count
                        FROM bookings b
                        JOIN departments d ON d.id = b.department_id
                        JOIN services s ON s.id = b.service_id
                        WHERE """ + bookingWhere("b", "d", "s", criteria) + """
                        GROUP BY COALESCE(b.external_source, b.source)
                        ORDER BY count DESC, name
                        """, params),
                simpleMetric("""
                        SELECT d.id, d.name, COUNT(*)::bigint AS count
                        FROM bookings b
                        JOIN departments d ON d.id = b.department_id
                        JOIN services s ON s.id = b.service_id
                        WHERE """ + bookingWhere("b", "d", "s", criteria) + """
                        GROUP BY d.id, d.name
                        ORDER BY count DESC, d.name
                        """, params),
                simpleMetric("""
                        SELECT s.id, s.name, COUNT(*)::bigint AS count
                        FROM bookings b
                        JOIN departments d ON d.id = b.department_id
                        JOIN services s ON s.id = b.service_id
                        WHERE """ + bookingWhere("b", "d", "s", criteria) + """
                        GROUP BY s.id, s.name
                        ORDER BY count DESC, s.name
                        """, params)
        );
    }

    public List<WindowWorkloadRow> windowWorkload(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        return jdbcTemplate.query("""
                SELECT
                  sw.id AS window_id,
                  sw.display_name AS window_number,
                  d.id AS department_id,
                  d.name AS department_name,
                  COUNT(t.id) FILTER (WHERE t.called_at IS NOT NULL) AS total_called,
                  COUNT(t.id) FILTER (WHERE t.status = 'COMPLETED') AS total_completed,
                  AVG(EXTRACT(EPOCH FROM (COALESCE(t.service_completed_at, t.completed_at) - t.service_started_at)))
                    FILTER (WHERE t.service_started_at IS NOT NULL AND COALESCE(t.service_completed_at, t.completed_at) IS NOT NULL) AS average_service_seconds,
                  (SUM(EXTRACT(EPOCH FROM (COALESCE(t.service_completed_at, t.completed_at) - t.service_started_at)))
                    FILTER (WHERE t.service_started_at IS NOT NULL AND COALESCE(t.service_completed_at, t.completed_at) IS NOT NULL))::bigint AS active_service_seconds
                FROM service_windows sw
                JOIN departments d ON d.id = sw.department_id
                LEFT JOIN tickets t ON t.current_window_id = sw.id
                  AND t.created_at >= :fromInstant
                  AND t.created_at < :toInstant
                LEFT JOIN services s ON s.id = t.service_id
                WHERE """ + departmentWhere("d", criteria) + """
                  AND (:windowId::uuid IS NULL OR sw.id = :windowId)
                GROUP BY sw.id, sw.display_name, d.id, d.name
                ORDER BY d.name, sw.display_name
                """, params, (rs, rowNum) -> new WindowWorkloadRow(
                mapper.uuid(rs, "window_id"),
                rs.getString("window_number"),
                mapper.uuid(rs, "department_id"),
                rs.getString("department_name"),
                mapper.longValue(rs, "total_called"),
                mapper.longValue(rs, "total_completed"),
                mapper.doubleValue(rs, "average_service_seconds"),
                mapper.nullableLong(rs, "active_service_seconds"),
                null,
                null
        ));
    }

    public List<WorkloadHourlyRow> workloadHourly(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        return jdbcTemplate.query("""
                SELECT
                  t.created_at::date AS work_date,
                  EXTRACT(HOUR FROM t.created_at)::int AS hour,
                  t.department_id,
                  COUNT(*)::bigint AS total_tickets,
                  COUNT(*) FILTER (WHERE t.status = 'COMPLETED')::bigint AS completed_tickets,
                  AVG(EXTRACT(EPOCH FROM (t.called_at - t.created_at))) FILTER (WHERE t.called_at IS NOT NULL) AS average_waiting_seconds,
                  AVG(EXTRACT(EPOCH FROM (COALESCE(t.service_completed_at, t.completed_at) - t.service_started_at)))
                    FILTER (WHERE t.service_started_at IS NOT NULL AND COALESCE(t.service_completed_at, t.completed_at) IS NOT NULL) AS average_service_seconds
                FROM tickets t
                JOIN departments d ON d.id = t.department_id
                JOIN services s ON s.id = t.service_id
                WHERE """ + ticketWhere("t", "d", "s", criteria) + """
                GROUP BY t.created_at::date, hour, t.department_id
                ORDER BY work_date, hour, t.department_id
                """, params, (rs, rowNum) -> {
            LocalDate date = mapper.localDate(rs, "work_date");
            UUID departmentId = mapper.uuid(rs, "department_id");
            long totalBookings = countBookingsForBucket(criteria, departmentId, date, mapper.intValue(rs, "hour"));
            return new WorkloadHourlyRow(
                    date,
                    mapper.intValue(rs, "hour"),
                    departmentId,
                    mapper.longValue(rs, "total_tickets"),
                    totalBookings,
                    mapper.longValue(rs, "completed_tickets"),
                    mapper.doubleValue(rs, "average_waiting_seconds"),
                    mapper.doubleValue(rs, "average_service_seconds")
            );
        });
    }

    public List<WorkloadDailyRow> workloadDaily(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        return jdbcTemplate.query("""
                SELECT
                  t.created_at::date AS work_date,
                  t.department_id,
                  COUNT(*)::bigint AS total_tickets,
                  COUNT(*) FILTER (WHERE t.status = 'COMPLETED')::bigint AS completed_tickets,
                  COUNT(*) FILTER (WHERE t.status = 'CANCELLED')::bigint AS cancelled_tickets,
                  COUNT(*) FILTER (WHERE t.status = 'NO_SHOW')::bigint AS no_show_tickets,
                  AVG(EXTRACT(EPOCH FROM (t.called_at - t.created_at))) FILTER (WHERE t.called_at IS NOT NULL) AS average_waiting_seconds,
                  AVG(EXTRACT(EPOCH FROM (COALESCE(t.service_completed_at, t.completed_at) - t.service_started_at)))
                    FILTER (WHERE t.service_started_at IS NOT NULL AND COALESCE(t.service_completed_at, t.completed_at) IS NOT NULL) AS average_service_seconds
                FROM tickets t
                JOIN departments d ON d.id = t.department_id
                JOIN services s ON s.id = t.service_id
                WHERE """ + ticketWhere("t", "d", "s", criteria) + """
                GROUP BY t.created_at::date, t.department_id
                ORDER BY work_date, t.department_id
                """, params, (rs, rowNum) -> {
            LocalDate date = mapper.localDate(rs, "work_date");
            UUID departmentId = mapper.uuid(rs, "department_id");
            return new WorkloadDailyRow(
                    date,
                    departmentId,
                    mapper.longValue(rs, "total_tickets"),
                    countBookingsForDay(criteria, departmentId, date),
                    mapper.longValue(rs, "completed_tickets"),
                    mapper.longValue(rs, "cancelled_tickets"),
                    mapper.longValue(rs, "no_show_tickets"),
                    mapper.doubleValue(rs, "average_waiting_seconds"),
                    mapper.doubleValue(rs, "average_service_seconds")
            );
        });
    }

    public PageResponse<TicketDetailRow> ticketDetails(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        long total = count("""
                SELECT COUNT(*)::bigint
                FROM tickets t
                JOIN departments d ON d.id = t.department_id
                JOIN services s ON s.id = t.service_id
                WHERE """ + ticketWhere("t", "d", "s", criteria), params);
        params.addValue("limit", criteria.size());
        params.addValue("offset", criteria.page() * criteria.size());
        List<TicketDetailRow> content = jdbcTemplate.query("""
                SELECT
                  t.id AS ticket_id,
                  t.ticket_number,
                  d.name AS department,
                  sw.display_name AS window,
                  s.name AS service,
                  t.source,
                  t.status,
                  t.created_at,
                  t.called_at,
                  t.service_started_at,
                  COALESCE(t.service_completed_at, t.completed_at) AS service_completed_at,
                  EXTRACT(EPOCH FROM (t.called_at - t.created_at))::bigint AS waiting_seconds,
                  EXTRACT(EPOCH FROM (COALESCE(t.service_completed_at, t.completed_at) - t.service_started_at))::bigint AS service_seconds,
                  u.full_name AS served_by_user,
                  t.citizen_full_name
                FROM tickets t
                JOIN departments d ON d.id = t.department_id
                JOIN services s ON s.id = t.service_id
                LEFT JOIN service_windows sw ON sw.id = t.current_window_id
                LEFT JOIN users u ON u.id = t.current_operator_id
                WHERE """ + ticketWhere("t", "d", "s", criteria) + """
                ORDER BY t.created_at DESC, t.id DESC
                LIMIT :limit OFFSET :offset
                """, params, (rs, rowNum) -> new TicketDetailRow(
                mapper.uuid(rs, "ticket_id"),
                rs.getString("ticket_number"),
                rs.getString("department"),
                rs.getString("window"),
                rs.getString("service"),
                rs.getString("source"),
                rs.getString("status"),
                mapper.instant(rs, "created_at"),
                mapper.instant(rs, "called_at"),
                mapper.instant(rs, "service_started_at"),
                mapper.instant(rs, "service_completed_at"),
                mapper.nullableLong(rs, "waiting_seconds"),
                mapper.nullableLong(rs, "service_seconds"),
                mapper.personalName(rs.getString("served_by_user"), criteria.includePersonalData()),
                mapper.personalName(rs.getString("citizen_full_name"), criteria.includePersonalData())
        ));
        return new PageResponse<>(content, criteria.page(), criteria.size(), total, totalPages(total, criteria.size()));
    }

    public PageResponse<BookingDetailRow> bookingDetails(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        long total = count("""
                SELECT COUNT(*)::bigint
                FROM bookings b
                JOIN departments d ON d.id = b.department_id
                JOIN services s ON s.id = b.service_id
                WHERE """ + bookingWhere("b", "d", "s", criteria), params);
        params.addValue("limit", criteria.size());
        params.addValue("offset", criteria.page() * criteria.size());
        List<BookingDetailRow> content = jdbcTemplate.query("""
                SELECT
                  b.id AS booking_id,
                  b.booking_number,
                  COALESCE(b.external_source, b.source) AS source,
                  COALESCE(b.external_id, b.external_booking_id) AS external_id,
                  d.name AS department,
                  s.name AS service,
                  b.booking_date,
                  b.booking_start,
                  b.booking_end,
                  b.status,
                  t.ticket_number,
                  b.citizen_full_name
                FROM bookings b
                JOIN departments d ON d.id = b.department_id
                JOIN services s ON s.id = b.service_id
                LEFT JOIN tickets t ON t.id = b.ticket_id
                WHERE """ + bookingWhere("b", "d", "s", criteria) + """
                ORDER BY b.booking_date DESC, b.booking_start DESC, b.id DESC
                LIMIT :limit OFFSET :offset
                """, params, (rs, rowNum) -> new BookingDetailRow(
                mapper.uuid(rs, "booking_id"),
                rs.getString("booking_number"),
                rs.getString("source"),
                rs.getString("external_id"),
                rs.getString("department"),
                rs.getString("service"),
                mapper.localDate(rs, "booking_date"),
                mapper.localTime(rs, "booking_start"),
                mapper.localTime(rs, "booking_end"),
                rs.getString("status"),
                rs.getString("ticket_number"),
                mapper.personalName(rs.getString("citizen_full_name"), criteria.includePersonalData())
        ));
        return new PageResponse<>(content, criteria.page(), criteria.size(), total, totalPages(total, criteria.size()));
    }

    public List<IntegrationReportRow> integrations(ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        return jdbcTemplate.query("""
                SELECT
                  ir.client_code,
                  COUNT(*)::bigint AS total_requests,
                  COUNT(*) FILTER (WHERE ir.status = 'SUCCEEDED')::bigint AS successful_requests,
                  COUNT(*) FILTER (WHERE ir.status = 'FAILED')::bigint AS failed_requests,
                  COUNT(*) FILTER (WHERE ir.idempotency_key IS NOT NULL AND ir.status = 'SUCCEEDED')::bigint AS duplicate_idempotent_requests,
                  COUNT(*) FILTER (WHERE ir.error_code = 'IDEMPOTENCY_CONFLICT')::bigint AS idempotency_conflicts,
                  NULL::numeric AS average_response_time_ms,
                  MAX(ir.created_at) AS last_request_at
                FROM integration_requests ir
                WHERE ir.created_at >= :fromInstant AND ir.created_at < :toInstant
                  AND (:source IS NULL OR ir.client_code = :source)
                GROUP BY ir.client_code
                ORDER BY ir.client_code
                """, params, (rs, rowNum) -> new IntegrationReportRow(
                rs.getString("client_code"),
                mapper.longValue(rs, "total_requests"),
                mapper.longValue(rs, "successful_requests"),
                mapper.longValue(rs, "failed_requests"),
                mapper.longValue(rs, "duplicate_idempotent_requests"),
                mapper.longValue(rs, "idempotency_conflicts"),
                mapper.doubleValue(rs, "average_response_time_ms"),
                mapper.instant(rs, "last_request_at")
        ));
    }

    private MapSqlParameterSource params(ReportCriteria criteria) {
        var filter = criteria.filter();
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("dateFrom", filter.getDateFrom());
        params.addValue("dateTo", filter.getDateTo());
        params.addValue("fromInstant", filter.getDateFrom().atStartOfDay().toInstant(ZoneOffset.UTC));
        params.addValue("toInstant", filter.getDateTo().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        params.addValue("regionId", filter.getRegionId());
        params.addValue("departmentId", filter.getDepartmentId());
        params.addValue("employeeId", filter.getEmployeeId());
        params.addValue("windowId", filter.getWindowId());
        params.addValue("serviceCategoryId", filter.getServiceCategoryId());
        params.addValue("serviceId", filter.getServiceId());
        params.addValue("source", filter.getSource());
        params.addValue("ticketStatus", filter.getTicketStatus());
        params.addValue("bookingStatus", filter.getBookingStatus());
        params.addValue("cancellationReasonId", filter.getCancellationReasonId());
        if (!criteria.allDepartments()) {
            params.addValue("departmentIds", criteria.departmentIds());
        }
        return params;
    }

    private String ticketWhere(String ticketAlias, String departmentAlias, String serviceAlias, ReportCriteria criteria) {
        List<String> clauses = new ArrayList<>();
        clauses.add(ticketAlias + ".created_at >= :fromInstant");
        clauses.add(ticketAlias + ".created_at < :toInstant");
        addDepartmentScope(clauses, ticketAlias + ".department_id", criteria);
        clauses.add("(:regionId::uuid IS NULL OR " + departmentAlias + ".region_id = :regionId)");
        clauses.add("(:employeeId::uuid IS NULL OR " + ticketAlias + ".current_operator_id = :employeeId)");
        clauses.add("(:windowId::uuid IS NULL OR " + ticketAlias + ".current_window_id = :windowId)");
        clauses.add("(:serviceCategoryId::uuid IS NULL OR " + serviceAlias + ".category_id = :serviceCategoryId)");
        clauses.add("(:serviceId::uuid IS NULL OR " + ticketAlias + ".service_id = :serviceId)");
        clauses.add("(:source IS NULL OR " + ticketAlias + ".source = :source)");
        clauses.add("(:ticketStatus IS NULL OR " + ticketAlias + ".status = :ticketStatus)");
        clauses.add("(:cancellationReasonId::uuid IS NULL OR " + ticketAlias + ".cancellation_reason_id = :cancellationReasonId)");
        return String.join(" AND ", clauses);
    }

    private String ticketWhereWithoutDepartment(String ticketAlias, String serviceAlias, ReportCriteria criteria) {
        List<String> clauses = new ArrayList<>();
        clauses.add(ticketAlias + ".created_at >= :fromInstant");
        clauses.add(ticketAlias + ".created_at < :toInstant");
        clauses.add("(:employeeId::uuid IS NULL OR " + ticketAlias + ".current_operator_id = :employeeId)");
        clauses.add("(:windowId::uuid IS NULL OR " + ticketAlias + ".current_window_id = :windowId)");
        clauses.add("(:serviceCategoryId::uuid IS NULL OR " + serviceAlias + ".category_id = :serviceCategoryId)");
        clauses.add("(:serviceId::uuid IS NULL OR " + ticketAlias + ".service_id = :serviceId)");
        clauses.add("(:source IS NULL OR " + ticketAlias + ".source = :source)");
        clauses.add("(:ticketStatus IS NULL OR " + ticketAlias + ".status = :ticketStatus)");
        clauses.add("(:cancellationReasonId::uuid IS NULL OR " + ticketAlias + ".cancellation_reason_id = :cancellationReasonId)");
        return String.join(" AND ", clauses);
    }

    private String ticketWhereWithKnownService(String ticketAlias, String departmentAlias, ReportCriteria criteria) {
        List<String> clauses = new ArrayList<>();
        clauses.add(ticketAlias + ".created_at >= :fromInstant");
        clauses.add(ticketAlias + ".created_at < :toInstant");
        addDepartmentScope(clauses, ticketAlias + ".department_id", criteria);
        clauses.add("(:regionId::uuid IS NULL OR " + departmentAlias + ".region_id = :regionId)");
        clauses.add("(:employeeId::uuid IS NULL OR " + ticketAlias + ".current_operator_id = :employeeId)");
        clauses.add("(:windowId::uuid IS NULL OR " + ticketAlias + ".current_window_id = :windowId)");
        clauses.add("(:source IS NULL OR " + ticketAlias + ".source = :source)");
        clauses.add("(:ticketStatus IS NULL OR " + ticketAlias + ".status = :ticketStatus)");
        clauses.add("(:cancellationReasonId::uuid IS NULL OR " + ticketAlias + ".cancellation_reason_id = :cancellationReasonId)");
        return String.join(" AND ", clauses);
    }

    private String bookingWhere(String bookingAlias, String departmentAlias, String serviceAlias, ReportCriteria criteria) {
        List<String> clauses = new ArrayList<>();
        clauses.add(bookingAlias + ".booking_date BETWEEN :dateFrom AND :dateTo");
        addDepartmentScope(clauses, bookingAlias + ".department_id", criteria);
        clauses.add("(:regionId::uuid IS NULL OR " + departmentAlias + ".region_id = :regionId)");
        clauses.add("(:serviceCategoryId::uuid IS NULL OR " + serviceAlias + ".category_id = :serviceCategoryId)");
        clauses.add("(:serviceId::uuid IS NULL OR " + bookingAlias + ".service_id = :serviceId)");
        clauses.add("(:source IS NULL OR COALESCE(" + bookingAlias + ".external_source, " + bookingAlias + ".source) = :source)");
        clauses.add("(:bookingStatus IS NULL OR " + bookingAlias + ".status = :bookingStatus)");
        clauses.add("(:cancellationReasonId::uuid IS NULL OR " + bookingAlias + ".cancellation_reason_id = :cancellationReasonId)");
        return String.join(" AND ", clauses);
    }

    private String bookingWhereWithoutDepartment(String bookingAlias, String serviceAlias, ReportCriteria criteria) {
        List<String> clauses = new ArrayList<>();
        clauses.add(bookingAlias + ".booking_date BETWEEN :dateFrom AND :dateTo");
        clauses.add("(:serviceCategoryId::uuid IS NULL OR " + serviceAlias + ".category_id = :serviceCategoryId)");
        clauses.add("(:serviceId::uuid IS NULL OR " + bookingAlias + ".service_id = :serviceId)");
        clauses.add("(:source IS NULL OR COALESCE(" + bookingAlias + ".external_source, " + bookingAlias + ".source) = :source)");
        clauses.add("(:bookingStatus IS NULL OR " + bookingAlias + ".status = :bookingStatus)");
        clauses.add("(:cancellationReasonId::uuid IS NULL OR " + bookingAlias + ".cancellation_reason_id = :cancellationReasonId)");
        return String.join(" AND ", clauses);
    }

    private String bookingWhereWithKnownService(String bookingAlias, String departmentAlias, ReportCriteria criteria) {
        List<String> clauses = new ArrayList<>();
        clauses.add(bookingAlias + ".booking_date BETWEEN :dateFrom AND :dateTo");
        addDepartmentScope(clauses, bookingAlias + ".department_id", criteria);
        clauses.add("(:regionId::uuid IS NULL OR " + departmentAlias + ".region_id = :regionId)");
        clauses.add("(:source IS NULL OR COALESCE(" + bookingAlias + ".external_source, " + bookingAlias + ".source) = :source)");
        clauses.add("(:bookingStatus IS NULL OR " + bookingAlias + ".status = :bookingStatus)");
        clauses.add("(:cancellationReasonId::uuid IS NULL OR " + bookingAlias + ".cancellation_reason_id = :cancellationReasonId)");
        return String.join(" AND ", clauses);
    }

    private String departmentWhere(String departmentAlias, ReportCriteria criteria) {
        List<String> clauses = new ArrayList<>();
        addDepartmentScope(clauses, departmentAlias + ".id", criteria);
        clauses.add("(:regionId::uuid IS NULL OR " + departmentAlias + ".region_id = :regionId)");
        return String.join(" AND ", clauses);
    }

    private String departmentScopeSql(String departmentAlias, ReportCriteria criteria) {
        if (criteria.allDepartments()) {
            return "";
        }
        if (criteria.departmentIds().isEmpty()) {
            return " AND 1 = 0";
        }
        return " AND " + departmentAlias + ".id IN (:departmentIds)";
    }

    private String regionScopeSql(ReportCriteria criteria) {
        if (criteria.allDepartments()) {
            return "";
        }
        if (criteria.departmentIds().isEmpty()) {
            return " AND 1 = 0";
        }
        return " AND EXISTS (SELECT 1 FROM departments ds WHERE ds.region_id = r.id AND ds.id IN (:departmentIds))";
    }

    private void addDepartmentScope(List<String> clauses, String departmentExpression, ReportCriteria criteria) {
        if (criteria.allDepartments()) {
            return;
        }
        if (criteria.departmentIds().isEmpty()) {
            clauses.add("1 = 0");
            return;
        }
        clauses.add(departmentExpression + " IN (:departmentIds)");
    }

    private String durationStatsSql(String endExpression, String startExpression, ReportCriteria criteria) {
        return """
                WITH durations AS (
                  SELECT EXTRACT(EPOCH FROM (""" + endExpression + " - " + startExpression + """
                  )) AS seconds
                  FROM tickets t
                  JOIN departments d ON d.id = t.department_id
                  JOIN services s ON s.id = t.service_id
                  WHERE """ + startExpression + " IS NOT NULL AND " + endExpression + " IS NOT NULL AND " + ticketWhere("t", "d", "s", criteria) + """
                )
                SELECT AVG(seconds) AS average_seconds,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY seconds) AS median_seconds,
                       MIN(seconds)::bigint AS min_seconds,
                       MAX(seconds)::bigint AS max_seconds,
                       percentile_cont(0.9) WITHIN GROUP (ORDER BY seconds) AS p90_seconds
                FROM durations
                """;
    }

    private List<TimeBucketRow> durationBuckets(String endExpression, String startExpression, ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        return jdbcTemplate.query("""
                WITH durations AS (
                  SELECT EXTRACT(EPOCH FROM (""" + endExpression + " - " + startExpression + """
                  )) AS seconds
                  FROM tickets t
                  JOIN departments d ON d.id = t.department_id
                  JOIN services s ON s.id = t.service_id
                  WHERE """ + startExpression + " IS NOT NULL AND " + endExpression + " IS NOT NULL AND " + ticketWhere("t", "d", "s", criteria) + """
                ),
                buckets AS (
                  SELECT CASE
                           WHEN seconds < 300 THEN '0-5 minutes'
                           WHEN seconds < 900 THEN '5-15 minutes'
                           WHEN seconds < 1800 THEN '15-30 minutes'
                           WHEN seconds < 3600 THEN '30-60 minutes'
                           ELSE '60+ minutes'
                         END AS bucket,
                         CASE
                           WHEN seconds < 300 THEN 1
                           WHEN seconds < 900 THEN 2
                           WHEN seconds < 1800 THEN 3
                           WHEN seconds < 3600 THEN 4
                           ELSE 5
                         END AS sort_order
                  FROM durations
                )
                SELECT bucket, COUNT(*)::bigint AS count
                FROM buckets
                GROUP BY bucket, sort_order
                ORDER BY sort_order
                """, params, (rs, rowNum) -> new TimeBucketRow(rs.getString("bucket"), mapper.longValue(rs, "count")));
    }

    private List<SimpleMetricRow> cancellationDepartments(UUID reasonId, ReportCriteria criteria) {
        MapSqlParameterSource params = params(criteria);
        params.addValue("reasonId", reasonId);
        return simpleMetric("""
                SELECT d.id, d.name, COUNT(*)::bigint AS count
                FROM tickets t
                JOIN departments d ON d.id = t.department_id
                JOIN services s ON s.id = t.service_id
                WHERE t.status = 'CANCELLED'
                  AND ((:reasonId::uuid IS NULL AND t.cancellation_reason_id IS NULL) OR t.cancellation_reason_id = :reasonId)
                  AND """ + ticketWhere("t", "d", "s", criteria) + """
                GROUP BY d.id, d.name
                ORDER BY count DESC, d.name
                """, params);
    }

    private long countBookingsForBucket(ReportCriteria criteria, UUID departmentId, LocalDate date, int hour) {
        MapSqlParameterSource params = params(criteria);
        params.addValue("bucketDepartmentId", departmentId);
        params.addValue("bucketDate", date);
        params.addValue("bucketHour", hour);
        return count("""
                SELECT COUNT(*)::bigint
                FROM bookings b
                JOIN departments d ON d.id = b.department_id
                JOIN services s ON s.id = b.service_id
                WHERE b.department_id = :bucketDepartmentId
                  AND b.booking_date = :bucketDate
                  AND EXTRACT(HOUR FROM b.booking_start)::int = :bucketHour
                  AND """ + bookingWhere("b", "d", "s", criteria), params);
    }

    private long countBookingsForDay(ReportCriteria criteria, UUID departmentId, LocalDate date) {
        MapSqlParameterSource params = params(criteria);
        params.addValue("bucketDepartmentId", departmentId);
        params.addValue("bucketDate", date);
        return count("""
                SELECT COUNT(*)::bigint
                FROM bookings b
                JOIN departments d ON d.id = b.department_id
                JOIN services s ON s.id = b.service_id
                WHERE b.department_id = :bucketDepartmentId
                  AND b.booking_date = :bucketDate
                  AND """ + bookingWhere("b", "d", "s", criteria), params);
    }

    private List<SimpleMetricRow> simpleMetric(String sql, MapSqlParameterSource params) {
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new SimpleMetricRow(
                mapper.uuid(rs, "id"),
                rs.getString("name"),
                mapper.longValue(rs, "count")
        ));
    }

    private List<HourMetricRow> hourMetric(String sql, MapSqlParameterSource params) {
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new HourMetricRow(
                mapper.intValue(rs, "hour"),
                mapper.longValue(rs, "count")
        ));
    }

    private String firstString(String sql, MapSqlParameterSource params, String key) {
        List<String> values = jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString(key));
        return values.isEmpty() ? null : values.get(0);
    }

    private Integer firstInteger(String sql, MapSqlParameterSource params, String key) {
        List<Integer> values = jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getInt(key));
        return values.isEmpty() ? null : values.get(0);
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbcTemplate.queryForObject(sql, params, Long.class);
        return value == null ? 0L : value;
    }

    private Long nullableLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private int totalPages(long total, int size) {
        if (size <= 0) {
            return 0;
        }
        return (int) Math.ceil(total * 1.0d / size);
    }
}
