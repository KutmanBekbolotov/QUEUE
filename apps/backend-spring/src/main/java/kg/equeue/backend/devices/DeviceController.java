package kg.equeue.backend.devices;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.terminals.TerminalEntity;
import kg.equeue.backend.terminals.TerminalRepository;
import kg.equeue.backend.tvdisplays.TvDisplayEntity;
import kg.equeue.backend.tvdisplays.TvDisplayRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
@Tag(name = "Devices")
public class DeviceController {

    private final TerminalRepository terminalRepository;
    private final TvDisplayRepository tvDisplayRepository;

    public DeviceController(TerminalRepository terminalRepository, TvDisplayRepository tvDisplayRepository) {
        this.terminalRepository = terminalRepository;
        this.tvDisplayRepository = tvDisplayRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TERMINAL_READ') or hasAuthority('TV_READ')")
    List<DeviceResponse> devices() {
        List<DeviceResponse> terminals = terminalRepository.findAll().stream()
                .map(this::terminalResponse)
                .toList();
        List<DeviceResponse> tvDisplays = tvDisplayRepository.findAll().stream()
                .map(this::tvDisplayResponse)
                .toList();
        return java.util.stream.Stream.concat(terminals.stream(), tvDisplays.stream())
                .sorted(Comparator.comparing(DeviceResponse::type).thenComparing(DeviceResponse::code))
                .toList();
    }

    private DeviceResponse terminalResponse(TerminalEntity entity) {
        return new DeviceResponse(
                entity.getId(),
                "TERMINAL",
                entity.getDepartmentId(),
                null,
                entity.getCode(),
                entity.getName(),
                entity.isActive(),
                entity.getLastSeenAt()
        );
    }

    private DeviceResponse tvDisplayResponse(TvDisplayEntity entity) {
        return new DeviceResponse(
                entity.getId(),
                "TV_DISPLAY",
                entity.getDepartmentId(),
                entity.getHallId(),
                entity.getCode(),
                entity.getName(),
                entity.isActive(),
                entity.getLastSeenAt()
        );
    }

    public record DeviceResponse(
            UUID id,
            String type,
            UUID departmentId,
            UUID hallId,
            String code,
            String name,
            boolean active,
            Instant lastSeenAt
    ) {
    }
}
