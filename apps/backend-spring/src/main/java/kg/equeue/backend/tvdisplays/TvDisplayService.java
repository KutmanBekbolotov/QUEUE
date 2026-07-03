package kg.equeue.backend.tvdisplays;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.common.DeviceTokenService;
import kg.equeue.backend.tickets.TicketDtos.TvSnapshotResponse;
import kg.equeue.backend.tickets.TicketService;
import kg.equeue.backend.tickets.TicketSseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class TvDisplayService {

    private final TvDisplayRepository tvDisplayRepository;
    private final DeviceTokenService deviceTokenService;
    private final TicketService ticketService;
    private final TicketSseService ticketSseService;

    public TvDisplayService(TvDisplayRepository tvDisplayRepository,
                            DeviceTokenService deviceTokenService,
                            TicketService ticketService,
                            TicketSseService ticketSseService) {
        this.tvDisplayRepository = tvDisplayRepository;
        this.deviceTokenService = deviceTokenService;
        this.ticketService = ticketService;
        this.ticketSseService = ticketSseService;
    }

    @Transactional
    public TvSnapshotResponse snapshot(UUID departmentId, HttpServletRequest request) {
        TvDisplayEntity display = requireDisplay(departmentId, request);
        display.setLastSeenAt(Instant.now());
        tvDisplayRepository.save(display);
        return ticketService.tvSnapshotForDevice(departmentId);
    }

    @Transactional
    public SseEmitter stream(UUID departmentId, HttpServletRequest request) {
        TvDisplayEntity display = requireDisplay(departmentId, request);
        display.setLastSeenAt(Instant.now());
        tvDisplayRepository.save(display);
        return ticketSseService.registerTv(departmentId);
    }

    private TvDisplayEntity requireDisplay(UUID departmentId, HttpServletRequest request) {
        TvDisplayEntity display = tvDisplayRepository.findFirstByDepartmentIdAndActiveTrue(departmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TV_DISPLAY_NOT_FOUND", "TV display was not found for department"));
        String rawToken = deviceTokenService.requireRawToken(request);
        if (!deviceTokenService.matches(rawToken, display.getTokenHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_DEVICE_TOKEN", "Invalid TV device token");
        }
        return display;
    }
}

