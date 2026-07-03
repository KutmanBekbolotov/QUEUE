package kg.equeue.backend.audit;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kg.equeue.backend.audit.dto.AuditLogResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    List<AuditLogResponse> recent(@RequestParam(defaultValue = "50") int limit) {
        return auditLogService.recent(limit);
    }
}
