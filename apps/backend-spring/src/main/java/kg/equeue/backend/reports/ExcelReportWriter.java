package kg.equeue.backend.reports;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ExcelReportWriter {

    public void write(ReportTable table, Path path) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200);
             OutputStream outputStream = Files.newOutputStream(path)) {
            SXSSFSheet sheet = workbook.createSheet("report");
            sheet.trackAllColumnsForAutoSizing();

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row header = sheet.createRow(0);
            for (int i = 0; i < table.headers().size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(table.headers().get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (List<Object> values : table.rows()) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < values.size(); i++) {
                    writeCell(row.createCell(i), values.get(i));
                }
            }

            for (int i = 0; i < table.headers().size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(outputStream);
            workbook.dispose();
        }
    }

    private void writeCell(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
            return;
        }
        if (value instanceof TemporalAccessor) {
            cell.setCellValue(value.toString());
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }
}
