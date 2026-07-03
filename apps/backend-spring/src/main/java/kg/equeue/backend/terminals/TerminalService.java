package kg.equeue.backend.terminals;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.common.DeviceTokenService;
import kg.equeue.backend.departmentservices.DepartmentServiceRepository;
import kg.equeue.backend.ticketevents.TicketActorType;
import kg.equeue.backend.tickets.TicketDtos.CreateTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.TicketResponse;
import kg.equeue.backend.tickets.TicketService;
import kg.equeue.backend.tickets.TicketSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TerminalService {

    private final TerminalRepository terminalRepository;
    private final DepartmentServiceRepository departmentServiceRepository;
    private final DeviceTokenService deviceTokenService;
    private final TicketService ticketService;

    public TerminalService(TerminalRepository terminalRepository,
                           DepartmentServiceRepository departmentServiceRepository,
                           DeviceTokenService deviceTokenService,
                           TicketService ticketService) {
        this.terminalRepository = terminalRepository;
        this.departmentServiceRepository = departmentServiceRepository;
        this.deviceTokenService = deviceTokenService;
        this.ticketService = ticketService;
    }

    @Transactional
    public TerminalDtos.TerminalConfigResponse config(UUID terminalId, HttpServletRequest request) {
        TerminalEntity terminal = terminalOrThrow(terminalId);
        requireValidToken(terminal, request);
        terminal.setLastSeenAt(Instant.now());
        terminalRepository.save(terminal);
        List<UUID> serviceIds = departmentServiceRepository
                .findByDepartmentIdAndActiveTrueOrderByServiceIdAsc(terminal.getDepartmentId())
                .stream()
                .filter(service -> service.isTerminalEnabled())
                .map(service -> service.getServiceId())
                .toList();
        return new TerminalDtos.TerminalConfigResponse(terminal.getId(), terminal.getDepartmentId(), terminal.getCode(), terminal.getName(), serviceIds);
    }

    @Transactional
    public TicketResponse createTicket(UUID terminalId, TerminalDtos.TerminalCreateTicketRequest request, HttpServletRequest httpRequest) {
        TerminalEntity terminal = terminalOrThrow(terminalId);
        requireValidToken(terminal, httpRequest);
        if (!terminal.getDepartmentId().equals(request.departmentId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TERMINAL_DEPARTMENT_DENIED", "Terminal cannot create tickets for another department");
        }
        CreateTicketRequest normalized = new CreateTicketRequest(
                request.departmentId(),
                request.serviceId(),
                null,
                request.citizenFullName(),
                request.citizenPin(),
                request.citizenPhone(),
                TicketSource.TERMINAL,
                request.comment(),
                null,
                null
        );
        terminal.setLastSeenAt(Instant.now());
        terminalRepository.save(terminal);
        return ticketService.createDeviceTicket(normalized, TicketActorType.DEVICE, terminal.getId(), httpRequest);
    }

    private TerminalEntity terminalOrThrow(UUID terminalId) {
        return terminalRepository.findById(terminalId)
                .filter(TerminalEntity::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TERMINAL_NOT_FOUND", "Terminal was not found or inactive"));
    }

    private void requireValidToken(TerminalEntity terminal, HttpServletRequest request) {
        String rawToken = deviceTokenService.requireRawToken(request);
        if (!deviceTokenService.matches(rawToken, terminal.getTokenHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_DEVICE_TOKEN", "Invalid terminal token");
        }
    }
}
