package kg.equeue.backend.reports;

import java.util.List;

public record ReportTable(
        String title,
        List<String> headers,
        List<List<Object>> rows
) {
    public int rowCount() {
        return rows == null ? 0 : rows.size();
    }
}
