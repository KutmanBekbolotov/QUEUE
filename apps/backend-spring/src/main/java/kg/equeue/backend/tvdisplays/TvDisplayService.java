package kg.equeue.backend.tvdisplays;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
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
    public TvSnapshotResponse snapshot(UUID displayId, HttpServletRequest request) {
        TvDisplayEntity display = requireDisplay(displayId, request);
        display.setLastSeenAt(Instant.now());
        tvDisplayRepository.save(display);
        return ticketService.tvSnapshotForDevice(display.getDepartmentId());
    }

    @Transactional
    public SseEmitter stream(UUID displayId, HttpServletRequest request) {
        TvDisplayEntity display = requireDisplay(displayId, request);
        display.setLastSeenAt(Instant.now());
        tvDisplayRepository.save(display);
        return ticketSseService.registerTv(display.getDepartmentId());
    }

    @Transactional
    public TvSnapshotResponse legacySnapshot(UUID departmentId, HttpServletRequest request) {
        TvDisplayEntity display = requireDisplayForDepartment(departmentId, request);
        display.setLastSeenAt(Instant.now());
        tvDisplayRepository.save(display);
        return ticketService.tvSnapshotForDevice(departmentId);
    }

    @Transactional
    public SseEmitter legacyStream(UUID departmentId, HttpServletRequest request) {
        TvDisplayEntity display = requireDisplayForDepartment(departmentId, request);
        display.setLastSeenAt(Instant.now());
        tvDisplayRepository.save(display);
        return ticketSseService.registerTv(departmentId);
    }

    private TvDisplayEntity requireDisplay(UUID displayId, HttpServletRequest request) {
        TvDisplayEntity display = tvDisplayRepository.findById(displayId)
                .filter(TvDisplayEntity::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TV_DISPLAY_NOT_FOUND", "TV display was not found or inactive"));
        String rawToken = deviceTokenService.requireRawToken(request);
        if (!deviceTokenService.matches(rawToken, display.getTokenHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_DEVICE_TOKEN", "Invalid TV device token");
        }
        return display;
    }

    private TvDisplayEntity requireDisplayForDepartment(UUID departmentId, HttpServletRequest request) {
        List<TvDisplayEntity> displays = tvDisplayRepository.findByDepartmentIdAndActiveTrueOrderByCodeAsc(departmentId);
        if (displays.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TV_DISPLAY_NOT_FOUND", "TV display was not found for department");
        }
        String rawToken = deviceTokenService.requireRawToken(request);
        return displays.stream()
                .filter(display -> deviceTokenService.matches(rawToken, display.getTokenHash()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_DEVICE_TOKEN", "Invalid TV device token"));
    }
}

