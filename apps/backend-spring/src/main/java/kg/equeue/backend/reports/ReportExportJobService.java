package kg.equeue.backend.reports;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.reports.ReportFileStorageService.StoredReportFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReportExportJobService {

    private final ReportExportRepository exportRepository;
    private final ReportsService reportsService;
    private final ReportFileStorageService storageService;
    private final CsvReportWriter csvWriter;
    private final ExcelReportWriter excelWriter;
    private final PdfReportWriter pdfWriter;
    private final AuditService auditService;
    private final ReportAggregationMapper mapper;
    private final int csvMaxRows;
    private final int xlsxMaxRows;
    private final int pdfMaxRows;

    public ReportExportJobService(ReportExportRepository exportRepository,
                                  ReportsService reportsService,
                                  ReportFileStorageService storageService,
                                  CsvReportWriter csvWriter,
                                  ExcelReportWriter excelWriter,
                                  PdfReportWriter pdfWriter,
                                  AuditService auditService,
                                  ReportAggregationMapper mapper,
                                  @Value("${app.reports.export.csv-max-rows:500000}") int csvMaxRows,
                                  @Value("${app.reports.export.xlsx-max-rows:100000}") int xlsxMaxRows,
                                  @Value("${app.reports.export.pdf-max-rows:5000}") int pdfMaxRows) {
        this.exportRepository = exportRepository;
        this.reportsService = reportsService;
        this.storageService = storageService;
        this.csvWriter = csvWriter;
        this.excelWriter = excelWriter;
        this.pdfWriter = pdfWriter;
        this.auditService = auditService;
        this.mapper = mapper;
        this.csvMaxRows = csvMaxRows;
        this.xlsxMaxRows = xlsxMaxRows;
        this.pdfMaxRows = pdfMaxRows;
    }

    public ReportExportEntity process(UUID exportId, ReportCriteria criteria, HttpServletRequest request) {
        ReportExportEntity export = exportRepository.findById(exportId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REPORT_EXPORT_NOT_FOUND", "Report export was not found"));
        export.setStatus(ReportExportStatus.PROCESSING);
        export.setStartedAt(Instant.now());
        exportRepository.save(export);

        try {
            int maxRows = maxRows(export.getExportFormat());
            ReportTable table = reportsService.tableForExport(export.getReportType(), criteria, maxRows);
            if (export.getExportFormat() == ReportExportFormat.PDF && table.rowCount() > pdfMaxRows) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_TOO_LARGE_FOR_PDF", "Report is too large for PDF export");
            }

            String fileName = fileName(export);
            StoredReportFile file = storageService.allocate(export.getId(), fileName);
            write(export.getExportFormat(), table, file);

            export.setFileBucket(file.bucket());
            export.setFileKey(file.key());
            export.setFileName(file.fileName());
            export.setFileSizeBytes(storageService.size(file.key()));
            export.setCompletedAt(Instant.now());
            export.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
            export.setStatus(ReportExportStatus.COMPLETED);
            export.setErrorMessage(null);
            exportRepository.save(export);
            auditService.write("REPORT_EXPORT_COMPLETED", "REPORT_EXPORT", export.getId(),
                    mapper.exportAuditJson(export.getReportType(), export.getExportFormat(), export.getId(), criteria.filter(), table.rowCount()), request);
            return export;
        } catch (ApiException ex) {
            fail(export, ex.getMessage(), criteria, request);
            throw ex;
        } catch (IOException | RuntimeException ex) {
            fail(export, ex.getMessage(), criteria, request);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORT_EXPORT_FAILED", "Report export failed");
        }
    }

    private void fail(ReportExportEntity export, String message, ReportCriteria criteria, HttpServletRequest request) {
        export.setStatus(ReportExportStatus.FAILED);
        export.setErrorMessage(message == null ? "Report export failed" : message);
        export.setCompletedAt(Instant.now());
        exportRepository.save(export);
        auditService.write("REPORT_EXPORT_FAILED", "REPORT_EXPORT", export.getId(),
                mapper.exportAuditJson(export.getReportType(), export.getExportFormat(), export.getId(), criteria.filter(), null), request);
    }

    private void write(ReportExportFormat format, ReportTable table, StoredReportFile file) throws IOException {
        switch (format) {
            case CSV -> csvWriter.write(table, file.path());
            case XLSX -> excelWriter.write(table, file.path());
            case PDF -> pdfWriter.write(table, file.path());
        }
    }

    private int maxRows(ReportExportFormat format) {
        return switch (format) {
            case CSV -> csvMaxRows;
            case XLSX -> xlsxMaxRows;
            case PDF -> pdfMaxRows + 1;
        };
    }

    private String fileName(ReportExportEntity export) {
        String extension = switch (export.getExportFormat()) {
            case CSV -> ".csv";
            case XLSX -> ".xlsx";
            case PDF -> ".pdf";
        };
        return export.getReportType().name().toLowerCase() + "_" + export.getId() + extension;
    }
}
