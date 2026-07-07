package kg.equeue.backend.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import kg.equeue.backend.reports.ReportDtos.ReportFilter;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ReportQueryRepositorySqlTest {

    private final CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
    private final ReportQueryRepository repository = new ReportQueryRepository(
            jdbcTemplate,
            new ReportAggregationMapper(new ObjectMapper())
    );

    @Test
    void whereHelpersProduceSeparatedSqlAndTypedNullableStringFilters() throws Exception {
        ReportCriteria criteria = criteria();

        String ticketWhere = invokeWhere("ticketWhere", "t", "d", "s", criteria);
        String bookingWhere = invokeWhere("bookingWhere", "b", "d", "s", criteria);
        String departmentWhere = invokeWhere("departmentWhere", "d", criteria);

        assertThat(ticketWhere)
                .startsWith(" t.created_at")
                .contains("CAST(:source AS varchar) IS NULL")
                .contains("CAST(:ticketStatus AS varchar) IS NULL")
                .doesNotContain(":source IS NULL")
                .doesNotContain(":ticketStatus IS NULL");
        assertThat(bookingWhere)
                .startsWith(" b.booking_date")
                .contains("CAST(:source AS varchar) IS NULL")
                .contains("CAST(:bookingStatus AS varchar) IS NULL")
                .doesNotContain(":source IS NULL")
                .doesNotContain(":bookingStatus IS NULL");
        assertThat(departmentWhere).startsWith(" (:regionId::uuid IS NULL");
    }

    @Test
    void paramsUseJdbcTimestampForInstantBounds() throws Exception {
        Method method = ReportQueryRepository.class.getDeclaredMethod("params", ReportCriteria.class);
        method.setAccessible(true);

        MapSqlParameterSource params = (MapSqlParameterSource) method.invoke(repository, criteria());

        assertThat(params.getValue("fromInstant")).isInstanceOf(Timestamp.class);
        assertThat(params.getValue("toInstant")).isInstanceOf(Timestamp.class);
    }

    @Test
    void integrationsReportUsesTypedNullableSourceFilter() {
        repository.integrations(criteria());

        assertThat(jdbcTemplate.capturedSql)
                .contains("CAST(:source AS varchar) IS NULL")
                .doesNotContain(":source IS NULL");
    }

    @Test
    void durationQueriesKeepWhereSeparated() throws Exception {
        ReportCriteria criteria = criteria();

        String statsSql = invokeWhere("durationStatsSql", "t.called_at", "t.created_at", criteria);
        invokePrivate("durationBuckets", "t.called_at", "t.created_at", criteria);

        assertThat(statsSql)
                .contains("WHERE t.created_at")
                .doesNotContain("WHEREt");
        assertThat(jdbcTemplate.capturedSql)
                .contains("WHERE t.created_at")
                .doesNotContain("WHEREt");
    }

    private String invokeWhere(String methodName, Object... args) throws Exception {
        return (String) invokePrivate(methodName, args);
    }

    private Object invokePrivate(String methodName, Object... args) throws Exception {
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = args[i].getClass();
        }
        Method method = ReportQueryRepository.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(repository, args);
    }

    private ReportCriteria criteria() {
        ReportFilter filter = new ReportFilter();
        filter.setDateFrom(LocalDate.of(2026, 6, 6));
        filter.setDateTo(LocalDate.of(2026, 7, 6));
        return new ReportCriteria(filter, null, true, false, true, null, 0, 50);
    }

    private static class CapturingJdbcTemplate extends NamedParameterJdbcTemplate {
        private String capturedSql;

        CapturingJdbcTemplate() {
            super(new DriverManagerDataSource());
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            this.capturedSql = sql;
            return List.of();
        }
    }
}
