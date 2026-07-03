package kg.equeue.backend.reports;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReportExportScheduler {

    private final ReportExportRepository exportRepository;

    public ReportExportScheduler(ReportExportRepository exportRepository) {
        this.exportRepository = exportRepository;
    }

    @Scheduled(fixedDelayString = "${app.reports.export.expire-scan-ms:3600000}")
    public void expireCompletedExports() {
        for (ReportExportEntity export : exportRepository.findTop100ByStatusAndExpiresAtBefore(ReportExportStatus.COMPLETED, Instant.now())) {
            export.setStatus(ReportExportStatus.EXPIRED);
            exportRepository.save(export);
        }
    }
}
