package kg.equeue.backend.reports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kg.equeue.backend.reports.ReportDtos.ReportFilter;
import org.springframework.stereotype.Component;

@Component
public class ReportAggregationMapper {

    private final ObjectMapper objectMapper;

    public ReportAggregationMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public long longValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public Long nullableLong(ResultSet rs, String key) throws SQLException {
        long value = rs.getLong(key);
        return rs.wasNull() ? null : value;
    }

    public long longValue(ResultSet rs, String key) throws SQLException {
        long value = rs.getLong(key);
        return rs.wasNull() ? 0L : value;
    }

    public int intValue(ResultSet rs, String key) throws SQLException {
        int value = rs.getInt(key);
        return rs.wasNull() ? 0 : value;
    }

    public Double doubleValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        return value instanceof Number number ? number.doubleValue() : null;
    }

    public Double doubleValue(ResultSet rs, String key) throws SQLException {
        double value = rs.getDouble(key);
        return rs.wasNull() ? null : value;
    }

    public UUID uuid(ResultSet rs, String key) throws SQLException {
        return rs.getObject(key, UUID.class);
    }

    public Instant instant(ResultSet rs, String key) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(key);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public LocalDate localDate(ResultSet rs, String key) throws SQLException {
        return rs.getObject(key, LocalDate.class);
    }

    public LocalTime localTime(ResultSet rs, String key) throws SQLException {
        return rs.getObject(key, LocalTime.class);
    }

    public String personalName(String value, boolean includePersonalData) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return includePersonalData ? value : maskName(value);
    }

    public String maskName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.trim().split("\\s+");
        StringBuilder masked = new StringBuilder();
        for (String part : parts) {
            if (!masked.isEmpty()) {
                masked.append(' ');
            }
            masked.append(part.charAt(0));
            if (part.length() > 1) {
                masked.append("***");
            }
        }
        return masked.toString();
    }

    public Double percent(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0.0d;
        }
        return Math.round((numerator * 10_000.0d / denominator)) / 100.0d;
    }

    public String auditJson(ReportType reportType, ReportFilter filter) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportType", reportType.name());
        payload.put("filters", safeFilters(filter));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"reportType\":\"" + reportType.name() + "\"}";
        }
    }

    public String exportAuditJson(ReportType reportType, ReportExportFormat format, UUID exportId, ReportFilter filter, Integer rowCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportType", reportType.name());
        payload.put("format", format.name());
        payload.put("exportId", exportId);
        payload.put("rowCount", rowCount);
        payload.put("filters", safeFilters(filter));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"reportType\":\"" + reportType.name() + "\"}";
        }
    }

    public Map<String, Object> safeFilters(ReportFilter filter) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (filter == null) {
            return values;
        }
        values.put("dateFrom", filter.getDateFrom());
        values.put("dateTo", filter.getDateTo());
        values.put("regionId", filter.getRegionId());
        values.put("departmentId", filter.getDepartmentId());
        values.put("employeeId", filter.getEmployeeId());
        values.put("windowId", filter.getWindowId());
        values.put("serviceCategoryId", filter.getServiceCategoryId());
        values.put("serviceId", filter.getServiceId());
        values.put("source", filter.getSource());
        values.put("ticketStatus", filter.getTicketStatus());
        values.put("bookingStatus", filter.getBookingStatus());
        values.put("cancellationReasonId", filter.getCancellationReasonId());
        values.put("groupBy", filter.getGroupBy());
        values.put("includePersonalData", filter.includePersonalData());
        values.put("page", filter.getPage());
        values.put("size", filter.getSize());
        return values;
    }
}
