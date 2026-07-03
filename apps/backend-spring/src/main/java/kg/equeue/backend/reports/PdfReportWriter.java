package kg.equeue.backend.reports;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

@Component
public class PdfReportWriter {

    private static final float MARGIN = 36f;
    private static final float LINE_HEIGHT = 14f;

    public void write(ReportTable table, Path path) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PdfPage page = newPage(document);
            writeLine(page, table.title(), true);
            writeLine(page, "Generated: " + Instant.now(), false);
            writeLine(page, String.join(" | ", table.headers()), true);
            for (List<Object> row : table.rows()) {
                writeLine(page, rowToText(row), false);
            }
            page.close();
            document.save(path.toFile());
        }
    }

    private PdfPage newPage(PDDocument document) throws IOException {
        PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
        document.addPage(page);
        PDPageContentStream content = new PDPageContentStream(document, page);
        content.setLeading(LINE_HEIGHT);
        content.beginText();
        content.newLineAtOffset(MARGIN, page.getMediaBox().getHeight() - MARGIN);
        return new PdfPage(document, page, content);
    }

    private void writeLine(PdfPage page, String value, boolean bold) throws IOException {
        if (page.y <= MARGIN) {
            page.close();
            PdfPage next = newPage(page.document);
            page.page = next.page;
            page.content = next.content;
            page.y = next.y;
        }
        page.content.setFont(new PDType1Font(bold ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA), 9);
        page.content.showText(safe(value));
        page.content.newLine();
        page.y -= LINE_HEIGHT;
    }

    private String rowToText(List<Object> row) {
        return row.stream()
                .map(value -> value == null ? "" : String.valueOf(value))
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    private static class PdfPage {
        private final PDDocument document;
        private PDPage page;
        private PDPageContentStream content;
        private float y;

        PdfPage(PDDocument document, PDPage page, PDPageContentStream content) {
            this.document = document;
            this.page = page;
            this.content = content;
            this.y = page.getMediaBox().getHeight() - MARGIN;
        }

        void close() throws IOException {
            content.endText();
            content.close();
        }
    }
}
