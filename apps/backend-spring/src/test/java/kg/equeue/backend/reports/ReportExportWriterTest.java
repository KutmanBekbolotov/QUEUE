package kg.equeue.backend.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class ReportExportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void masksPersonalNamesByDefault() {
        ReportAggregationMapper mapper = new ReportAggregationMapper(new ObjectMapper());

        assertThat(mapper.personalName("Ada Lovelace", false)).isEqualTo("A*** L***");
        assertThat(mapper.personalName("Ada Lovelace", true)).isEqualTo("Ada Lovelace");
    }

    @Test
    void csvWriterAddsBomAndEscapesCells() throws Exception {
        CsvReportWriter writer = new CsvReportWriter(";");
        Path file = tempDir.resolve("report.csv");

        writer.write(new ReportTable("test", List.of("Name", "Count"), List.of(List.of("A;B", 2))), file);

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(content.charAt(0)).isEqualTo('\ufeff');
        assertThat(content).contains("\"A;B\";2");
    }
}
