package kg.equeue.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import kg.equeue.backend.tickets.TicketDomainEventPublisher.TicketDomainEvent;
import kg.equeue.backend.tickets.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;

class RabbitMqConfigTest {

    private final RabbitMqConfig config = new RabbitMqConfig();

    @Test
    void domainEventMessageConverterSerializesRecordPayloadsAsJson() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        var converter = config.domainEventMessageConverter(objectMapper);
        var event = new TicketDomainEvent(
                UUID.randomUUID(),
                "ticket.created",
                UUID.randomUUID(),
                "TS-001",
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                TicketStatus.WAITING,
                Instant.parse("2026-01-01T00:00:00Z"));

        var message = converter.toMessage(event, new MessageProperties());

        assertThat(new String(message.getBody())).contains("\"eventType\":\"ticket.created\"");
        assertThat(message.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
    }
}
