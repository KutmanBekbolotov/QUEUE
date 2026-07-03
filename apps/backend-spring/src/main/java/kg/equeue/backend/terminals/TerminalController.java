package kg.equeue.backend.terminals;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import kg.equeue.backend.tickets.TicketDtos.TicketResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/terminal/{terminalId}")
@Tag(name = "Terminal Devices")
public class TerminalController {

    private final TerminalService terminalService;

    public TerminalController(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    @GetMapping("/config")
    TerminalDtos.TerminalConfigResponse config(@PathVariable UUID terminalId, HttpServletRequest request) {
        return terminalService.config(terminalId, request);
    }

    @PostMapping("/tickets")
    TicketResponse createTicket(@PathVariable UUID terminalId,
                                @Valid @RequestBody TerminalDtos.TerminalCreateTicketRequest request,
                                HttpServletRequest httpRequest) {
        return terminalService.createTicket(terminalId, request, httpRequest);
    }
}
