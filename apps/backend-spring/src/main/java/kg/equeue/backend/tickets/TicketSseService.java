package kg.equeue.backend.tickets;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class TicketSseService {

    private static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(5).toMillis();

    private final Map<UUID, Set<SseEmitter>> tvEmitters = new ConcurrentHashMap<>();
    private final Map<UUID, Set<SseEmitter>> operatorEmitters = new ConcurrentHashMap<>();

    public SseEmitter registerTv(UUID departmentId) {
        return register(tvEmitters, departmentId);
    }

    public SseEmitter registerOperator(UUID windowId) {
        return register(operatorEmitters, windowId);
    }

    public void publish(TicketDomainEventPublisher.TicketDomainEvent event) {
        publishTo(tvEmitters.get(event.departmentId()), event);
        if (event.windowId() != null) {
            publishTo(operatorEmitters.get(event.windowId()), event);
        }
    }

    private SseEmitter register(Map<UUID, Set<SseEmitter>> registry, UUID key) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        registry.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable cleanup = () -> cleanup(registry, key, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(error -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("key", key.toString())));
        } catch (IOException ex) {
            close(registry, key, emitter, ex);
        }
        return emitter;
    }

    private void publishTo(Set<SseEmitter> emitters, TicketDomainEventPublisher.TicketDomainEvent event) {
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : Set.copyOf(emitters)) {
            try {
                emitter.send(SseEmitter.event().name(event.eventType()).data(event));
            } catch (IOException ex) {
                close(emitters, emitter, ex);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.sse.heartbeat-ms:25000}")
    void heartbeat() {
        heartbeat(tvEmitters);
        heartbeat(operatorEmitters);
    }

    private void heartbeat(Map<UUID, Set<SseEmitter>> registry) {
        registry.forEach((key, emitters) -> {
            for (SseEmitter emitter : Set.copyOf(emitters)) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException ex) {
                    close(registry, key, emitter, ex);
                }
            }
        });
    }

    private void cleanup(Map<UUID, Set<SseEmitter>> registry, UUID key, SseEmitter emitter) {
        Set<SseEmitter> emitters = registry.get(key);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            registry.remove(key, emitters);
        }
    }

    private void close(Map<UUID, Set<SseEmitter>> registry, UUID key, SseEmitter emitter, IOException ex) {
        cleanup(registry, key, emitter);
        emitter.completeWithError(ex);
    }

    private void close(Set<SseEmitter> emitters, SseEmitter emitter, IOException ex) {
        emitters.remove(emitter);
        emitter.completeWithError(ex);
    }
}
