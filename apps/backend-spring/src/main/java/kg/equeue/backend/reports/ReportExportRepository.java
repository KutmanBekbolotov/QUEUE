package kg.equeue.backend.reports;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportExportRepository extends JpaRepository<ReportExportEntity, UUID> {

    List<ReportExportEntity> findTop100ByStatusAndExpiresAtBefore(ReportExportStatus status, Instant now);
}
