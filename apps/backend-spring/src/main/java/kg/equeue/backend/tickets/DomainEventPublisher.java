package kg.equeue.backend.tickets;

import kg.equeue.backend.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class DomainEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public DomainEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishTicketEvent(String routingKey, Object payload) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.DOMAIN_EVENTS_EXCHANGE, routingKey, payload);
    }
}

