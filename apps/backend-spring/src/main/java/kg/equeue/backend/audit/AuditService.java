package kg.equeue.backend.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import kg.equeue.backend.auth.AuthenticatedPrincipal;
import kg.equeue.backend.common.RequestContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(String action, String entityType, UUID entityId, String newValue, HttpServletRequest request) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setActorType(actorType());
        entity.setActorId(actorId());
        entity.setAction(action);
        entity.setEntityType(entityType);
        entity.setEntityId(entityId);
        entity.setNewValue(newValue);
        entity.setIp(clientIp(request));
        entity.setUserAgent(request == null ? null : request.getHeader("User-Agent"));
        entity.setSource("BACKEND");
        entity.setRequestId(RequestContext.requestId());
        auditLogRepository.save(entity);
    }

    private String actorType() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal) {
            return "USER";
        }
        return "SYSTEM";
    }

    private UUID actorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            return principal.id();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

