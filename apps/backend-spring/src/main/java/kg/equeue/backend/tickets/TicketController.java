package kg.equeue.backend.tickets;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.tickets.TicketDtos.CallNextTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.CallTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.CancelTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.CreateTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.PauseTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.TicketResponse;
import kg.equeue.backend.tickets.TicketDtos.TransferTicketRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TICKET_CREATE')")
    TicketResponse create(@Valid @RequestBody CreateTicketRequest request, HttpServletRequest httpRequest) {
        return ticketService.create(request, httpRequest);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TICKET_READ')")
    List<TicketResponse> list(@RequestParam(required = false) UUID departmentId) {
        return ticketService.list(departmentId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TICKET_READ')")
    TicketResponse get(@PathVariable UUID id) {
        return ticketService.get(id);
    }

    @PostMapping("/{id}/call")
    @PreAuthorize("hasAuthority('TICKET_CALL')")
    TicketResponse call(@PathVariable UUID id, @Valid @RequestBody CallTicketRequest request, HttpServletRequest httpRequest) {
        return ticketService.call(id, request, httpRequest);
    }

    @PostMapping("/call-next")
    @PreAuthorize("hasAuthority('TICKET_CALL')")
    TicketResponse callNext(@Valid @RequestBody CallNextTicketRequest request, HttpServletRequest httpRequest) {
        return ticketService.callNext(request, httpRequest);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('TICKET_START')")
    TicketResponse start(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return ticketService.start(id, httpRequest);
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAuthority('TICKET_PAUSE')")
    TicketResponse pause(@PathVariable UUID id, @RequestBody(required = false) PauseTicketRequest request, HttpServletRequest httpRequest) {
        return ticketService.pause(id, request == null ? new PauseTicketRequest(null, null) : request, httpRequest);
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('TICKET_RESUME')")
    TicketResponse resume(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return ticketService.resume(id, httpRequest);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('TICKET_COMPLETE')")
    TicketResponse complete(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return ticketService.complete(id, httpRequest);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('TICKET_CANCEL')")
    TicketResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelTicketRequest request, HttpServletRequest httpRequest) {
        return ticketService.cancel(id, request, httpRequest);
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasAuthority('TICKET_NO_SHOW')")
    TicketResponse noShow(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return ticketService.noShow(id, httpRequest);
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasAuthority('TICKET_TRANSFER')")
    TicketResponse transfer(@PathVariable UUID id, @Valid @RequestBody TransferTicketRequest request, HttpServletRequest httpRequest) {
        return ticketService.transfer(id, request, httpRequest);
    }
}
