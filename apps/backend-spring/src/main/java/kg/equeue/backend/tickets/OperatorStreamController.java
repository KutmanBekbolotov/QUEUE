package kg.equeue.backend.tickets;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import kg.equeue.backend.common.DepartmentScopeService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/operator")
@Tag(name = "Operator Streams")
public class OperatorStreamController {

    private final TicketSseService ticketSseService;
    private final DepartmentScopeService departmentScopeService;

    public OperatorStreamController(TicketSseService ticketSseService, DepartmentScopeService departmentScopeService) {
        this.ticketSseService = ticketSseService;
        this.departmentScopeService = departmentScopeService;
    }

    @GetMapping(value = "/{windowId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('TICKET_READ')")
    SseEmitter stream(@PathVariable UUID windowId, HttpServletResponse response) {
        departmentScopeService.requireWindowAccess(windowId);
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        return ticketSseService.registerOperator(windowId);
    }
}
