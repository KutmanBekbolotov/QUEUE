package kg.equeue.backend.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAuditLogRepository extends JpaRepository<LoginAuditLogEntity, UUID> {
}

