package kg.equeue.backend.reports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.reports.ReportDtos.ExportRequest;
import kg.equeue.backend.reports.ReportDtos.ExportResponse;
import kg.equeue.backend.reports.ReportDtos.ReportFilter;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ReportExportService {

    private final ReportExportRepository exportRepository;
    private final ReportsService reportsService;
    private final ReportExportJobService jobService;
    private final ReportPermissionService permissionService;
    private final ReportFileStorageService storageService;
    private final AuditService auditService;
    private final ReportAggregationMapper mapper;
    private final ObjectMapper objectMapper;

    public ReportExportService(ReportExportRepository exportRepository,
                               ReportsService reportsService,
                               ReportExportJobService jobService,
                               ReportPermissionService permissionService,
                               ReportFileStorageService storageService,
                               AuditService auditService,
                               ReportAggregationMapper mapper,
                               ObjectMapper objectMapper) {
        this.exportRepository = exportRepository;
        this.reportsService = reportsService;
        this.jobService = jobService;
        this.permissionService = permissionService;
        this.storageService = storageService;
        this.auditService = auditService;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public ExportResponse requestExport(ExportRequest request, HttpServletRequest httpRequest) {
        ReportCriteria criteria = reportsService.validateForExport(request);

        ReportExportEntity export = new ReportExportEntity();
        export.setReportType(request.reportType());
        export.setExportFormat(request.format());
        export.setStatus(ReportExportStatus.PENDING);
        export.setRequestedByUserId(permissionService.currentUserId());
        export.setDepartmentId(request.filters().getDepartmentId());
        export.setFiltersJson(filtersJson(request.filters()));
        ReportExportEntity saved = exportRepository.save(export);

        auditService.write("REPORT_EXPORT_REQUESTED", "REPORT_EXPORT", saved.getId(),
                mapper.exportAuditJson(saved.getReportType(), saved.getExportFormat(), saved.getId(), request.filters(), null), httpRequest);

        ReportExportEntity processed = jobService.process(saved.getId(), criteria, httpRequest);
        return response(processed);
    }

    public ExportResponse getStatus(UUID id) {
        ReportExportEntity export = exportOrThrow(id);
        permissionService.requireDownloadAccess(export, readFilter(export));
        return response(export);
    }

    public ResponseEntity<Resource> download(UUID id, HttpServletRequest request) {
        ReportExportEntity export = exportOrThrow(id);
        ReportFilter filter = readFilter(export);
        permissionService.requireDownloadAccess(export, filter);
        if (export.getStatus() == ReportExportStatus.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_EXPORT_FAILED", "Report export failed");
        }
        if (export.getStatus() != ReportExportStatus.COMPLETED || export.getFileKey() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_EXPORT_NOT_READY", "Report export is not ready");
        }
        try {
            Resource resource = storageService.load(export.getFileKey());
            auditService.write("REPORT_EXPORT_DOWNLOADED", "REPORT_EXPORT", export.getId(),
                    mapper.exportAuditJson(export.getReportType(), export.getExportFormat(), export.getId(), filter, null), request);
            return ResponseEntity.ok()
                    .contentType(mediaType(export.getExportFormat()))
                    .contentLength(export.getFileSizeBytes() == null ? resource.contentLength() : export.getFileSizeBytes())
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(export.getFileName()).build().toString())
                    .body(resource);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORT_EXPORT_FAILED", "Report export file could not be read");
        }
    }

    private ReportExportEntity exportOrThrow(UUID id) {
        return exportRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REPORT_EXPORT_NOT_FOUND", "Report export was not found"));
    }

    private ExportResponse response(ReportExportEntity export) {
        String downloadUrl = export.getStatus() == ReportExportStatus.COMPLETED
                ? "/api/v1/reports/export/" + export.getId() + "/download"
                : null;
        return new ExportResponse(
                export.getId(),
                export.getReportType(),
                export.getExportFormat(),
                export.getStatus(),
                export.getFileName(),
                export.getFileSizeBytes(),
                export.getCreatedAt(),
                export.getCompletedAt(),
                downloadUrl,
                export.getErrorMessage()
        );
    }

    private String filtersJson(ReportFilter filter) {
        try {
            return objectMapper.writeValueAsString(mapper.safeFilters(filter));
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private ReportFilter readFilter(ReportExportEntity export) {
        try {
            return objectMapper.readValue(export.getFiltersJson(), ReportFilter.class);
        } catch (IOException ex) {
            return new ReportFilter();
        }
    }

    private MediaType mediaType(ReportExportFormat format) {
        return switch (format) {
            case CSV -> new MediaType("text", "csv");
            case XLSX -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case PDF -> MediaType.APPLICATION_PDF;
        };
    }
}
