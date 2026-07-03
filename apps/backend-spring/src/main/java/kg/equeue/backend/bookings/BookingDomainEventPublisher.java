package kg.equeue.backend.bookings;

import java.time.Instant;
import java.util.UUID;
import kg.equeue.backend.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class BookingDomainEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public BookingDomainEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public BookingDomainEvent publish(String eventType, BookingEntity booking) {
        BookingDomainEvent event = new BookingDomainEvent(
                UUID.randomUUID(),
                eventType,
                booking.getId(),
                booking.getBookingNumber(),
                booking.getTicketId(),
                booking.getDepartmentId(),
                booking.getServiceId(),
                booking.getSlotId(),
                booking.getExternalSource(),
                booking.getExternalId(),
                booking.getStatus(),
                Instant.now()
        );
        rabbitTemplate.convertAndSend(RabbitMqConfig.DOMAIN_EVENTS_EXCHANGE, eventType, event);
        return event;
    }

    public record BookingDomainEvent(
            UUID eventId,
            String eventType,
            UUID bookingId,
            String bookingNumber,
            UUID ticketId,
            UUID departmentId,
            UUID serviceId,
            UUID slotId,
            BookingSource source,
            String externalId,
            BookingStatus status,
            Instant occurredAt
    ) {
    }
}

