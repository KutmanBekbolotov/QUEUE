package kg.equeue.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String DOMAIN_EVENTS_EXCHANGE = "equeue.domain-events";
    public static final String TICKET_EVENTS_QUEUE = "equeue.ticket-events";
    public static final String BOOKING_EVENTS_QUEUE = "equeue.booking-events";

    @Bean
    TopicExchange domainEventsExchange() {
        return new TopicExchange(DOMAIN_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    Queue ticketEventsQueue() {
        return new Queue(TICKET_EVENTS_QUEUE, true);
    }

    @Bean
    Queue bookingEventsQueue() {
        return new Queue(BOOKING_EVENTS_QUEUE, true);
    }

    @Bean
    Binding ticketEventsBinding(Queue ticketEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(ticketEventsQueue).to(domainEventsExchange).with("ticket.*");
    }

    @Bean
    Binding bookingEventsBinding(Queue bookingEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(bookingEventsQueue).to(domainEventsExchange).with("booking.*");
    }

    @Bean
    MessageConverter domainEventMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
