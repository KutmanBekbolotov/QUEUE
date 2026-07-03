package kg.equeue.backend.reports;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CsvReportWriter {

    private final String delimiter;

    public CsvReportWriter(@Value("${app.reports.export.csv-delimiter:;}") String delimiter) {
        this.delimiter = delimiter == null || delimiter.isBlank() ? ";" : delimiter;
    }

    public void write(ReportTable table, Path path) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write('\ufeff');
            writeRow(writer, table.headers());
            for (List<Object> row : table.rows()) {
                writeRow(writer, row);
            }
        }
    }

    private void writeRow(BufferedWriter writer, List<?> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(delimiter);
            }
            writer.write(escape(format(values.get(i))));
        }
        writer.newLine();
    }

    private String escape(String value) {
        if (value.contains("\"") || value.contains("\n") || value.contains("\r") || value.contains(delimiter)) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof TemporalAccessor) {
            return value.toString();
        }
        return String.valueOf(value);
    }
}
