package kg.equeue.backend.tvdisplays;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import kg.equeue.backend.tickets.TicketDtos.TvSnapshotResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/tv")
@Tag(name = "TV Displays")
public class TvDisplayController {

    private final TvDisplayService tvDisplayService;

    public TvDisplayController(TvDisplayService tvDisplayService) {
        this.tvDisplayService = tvDisplayService;
    }

    @GetMapping("/displays/{displayId}/snapshot")
    TvSnapshotResponse snapshot(@PathVariable UUID displayId, HttpServletRequest request) {
        return tvDisplayService.snapshot(displayId, request);
    }

    @GetMapping("/displays/{displayId}/stream")
    SseEmitter stream(@PathVariable UUID displayId, HttpServletRequest request) {
        return tvDisplayService.stream(displayId, request);
    }

    @Deprecated
    @GetMapping("/{departmentId}/snapshot")
    TvSnapshotResponse legacySnapshot(@PathVariable UUID departmentId, HttpServletRequest request) {
        return tvDisplayService.legacySnapshot(departmentId, request);
    }

    @Deprecated
    @GetMapping("/{departmentId}/stream")
    SseEmitter legacyStream(@PathVariable UUID departmentId, HttpServletRequest request) {
        return tvDisplayService.legacyStream(departmentId, request);
    }
}
