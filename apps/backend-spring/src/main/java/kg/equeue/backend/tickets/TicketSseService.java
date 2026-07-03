package kg.equeue.backend.tickets;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class TicketSseService {

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
        SseEmitter emitter = new SseEmitter(0L);
        registry.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable cleanup = () -> registry.getOrDefault(key, Set.of()).remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("key", key.toString())));
        } catch (IOException ex) {
            cleanup.run();
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
                emitters.remove(emitter);
            }
        }
    }
}

