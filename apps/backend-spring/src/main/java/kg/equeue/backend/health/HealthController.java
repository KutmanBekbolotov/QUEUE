package kg.equeue.backend.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health")
public class HealthController {

    @GetMapping
    @Operation(summary = "Backend health check")
    Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "backend-spring",
                "timestamp", Instant.now()
        );
    }
}
