package kg.equeue.backend.audit;

import java.util.List;
import kg.equeue.backend.audit.dto.AuditLogResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return auditLogRepository.findAll(PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditLogResponse toResponse(AuditLogEntity entity) {
        return new AuditLogResponse(
                entity.getId(),
                entity.getActorType(),
                entity.getActorId(),
                entity.getAction(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getIp(),
                entity.getSource(),
                entity.getRequestId(),
                entity.getCreatedAt()
        );
    }
}

