package kg.equeue.backend.tickets;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashSet;
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
    private final Map<UUID, Set<SseEmitter>> operatorWindowEmitters = new ConcurrentHashMap<>();
    private final Map<UUID, Set<SseEmitter>> operatorDepartmentEmitters = new ConcurrentHashMap<>();

    public SseEmitter registerTv(UUID departmentId) {
        return register(tvEmitters, departmentId);
    }

    public SseEmitter registerOperator(UUID windowId, UUID departmentId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        operatorWindowEmitters.computeIfAbsent(windowId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        operatorDepartmentEmitters.computeIfAbsent(departmentId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable cleanup = () -> {
            cleanup(operatorWindowEmitters, windowId, emitter);
            cleanup(operatorDepartmentEmitters, departmentId, emitter);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(error -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().comment("connected"));
            emitter.send(SseEmitter.event().name("connected").data(Map.of(
                    "windowId", windowId.toString(),
                    "departmentId", departmentId.toString()
            )));
        } catch (IOException ex) {
            cleanup.run();
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    public void publish(TicketDomainEventPublisher.TicketDomainEvent event) {
        publishTo(tvEmitters.get(event.departmentId()), event);
        Set<SseEmitter> operatorTargets = new LinkedHashSet<>();
        Set<SseEmitter> departmentEmitters = operatorDepartmentEmitters.get(event.departmentId());
        if (departmentEmitters != null) {
            operatorTargets.addAll(departmentEmitters);
        }
        if (event.windowId() != null) {
            Set<SseEmitter> windowEmitters = operatorWindowEmitters.get(event.windowId());
            if (windowEmitters != null) {
                operatorTargets.addAll(windowEmitters);
            }
        }
        if (!operatorTargets.isEmpty()) {
            publishTo(operatorTargets, event);
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
            emitter.send(SseEmitter.event().comment("connected"));
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
                for (String eventName : sseEventNames(event.eventType())) {
                    emitter.send(SseEmitter.event().name(eventName).data(event));
                }
            } catch (IOException ex) {
                close(emitters, emitter, ex);
            }
        }
    }

    private Set<String> sseEventNames(String eventType) {
        Set<String> names = new LinkedHashSet<>();
        names.add(eventType);
        names.add(eventType.replace('.', '_'));
        if ("ticket.paused".equals(eventType)) {
            names.add("service_paused");
        }
        return names;
    }

    @Scheduled(fixedDelayString = "${app.sse.heartbeat-ms:25000}")
    void heartbeat() {
        heartbeat(tvEmitters);
        heartbeat(operatorWindowEmitters);
        heartbeat(operatorDepartmentEmitters);
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
