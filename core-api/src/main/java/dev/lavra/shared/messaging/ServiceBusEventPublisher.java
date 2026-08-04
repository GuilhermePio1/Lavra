package dev.lavra.shared.messaging;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes events to Azure Service Bus — the emulator locally, the real
 * namespace in Azure.
 */
class ServiceBusEventPublisher implements EventPublisher, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ServiceBusEventPublisher.class);

    private final ServiceBusClientBuilder clientBuilder;
    private final ObjectMapper mapper;
    private final Clock clock;

    /**
     * One sender per queue, opened on first use and kept: each carries an AMQP
     * connection, so building one per message would pay a handshake per event.
     * Lazy rather than eager because the context must start without a broker.
     */
    private final Map<String, ServiceBusSenderClient> senders = new ConcurrentHashMap<>();

    ServiceBusEventPublisher(ServiceBusClientBuilder clientBuilder, ObjectMapper mapper, Clock clock) {
        this.clientBuilder = clientBuilder;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public UUID publish(EventType type, Object payload) {
        DomainEvent event = DomainEvent.of(type, payload, clock);

        ServiceBusMessage message = new ServiceBusMessage(BinaryData.fromString(mapper.writeValueAsString(event)))
                .setContentType("application/json")
                // The broker's own duplicate detection keys on this, and so does
                // the consumer: same eventId, same fact, however many deliveries.
                .setMessageId(event.eventId().toString())
                .setSubject(type.wireName());

        sender(type).sendMessage(message);
        log.info("Published {} eventId={} queue={}", type.wireName(), event.eventId(), type.queue());
        return event.eventId();
    }

    private ServiceBusSenderClient sender(EventType type) {
        return senders.computeIfAbsent(type.queue(),
                queue -> clientBuilder.sender().queueName(queue).buildClient());
    }

    @Override
    public void close() {
        senders.values().forEach(ServiceBusSenderClient::close);
        senders.clear();
    }
}
