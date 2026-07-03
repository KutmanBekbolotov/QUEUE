package kg.equeue.backend.tickets;

import java.time.Instant;
import java.util.UUID;
import kg.equeue.backend.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class TicketDomainEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final TicketSseService ticketSseService;

    public TicketDomainEventPublisher(RabbitTemplate rabbitTemplate, TicketSseService ticketSseService) {
        this.rabbitTemplate = rabbitTemplate;
        this.ticketSseService = ticketSseService;
    }

    public TicketDomainEvent publish(String eventType, TicketEntity ticket) {
        TicketDomainEvent event = new TicketDomainEvent(
                UUID.randomUUID(),
                eventType,
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getDepartmentId(),
                ticket.getWindowId(),
                ticket.getServiceId(),
                ticket.getStatus(),
                Instant.now()
        );
        rabbitTemplate.convertAndSend(RabbitMqConfig.DOMAIN_EVENTS_EXCHANGE, eventType, event);
        ticketSseService.publish(event);
        return event;
    }

    public record TicketDomainEvent(
            UUID eventId,
            String eventType,
            UUID ticketId,
            String ticketNumber,
            UUID departmentId,
            UUID windowId,
            UUID serviceId,
            TicketStatus status,
            Instant occurredAt
    ) {
    }
}

