package dev.lavra.shared.messaging;

import com.azure.core.exception.AzureException;
import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import java.io.IOException;
import java.io.UncheckedIOException;
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
 *
 * <p>This class is the boundary of the Azure SDK: broker failures leave it as
 * an {@link EventPublishException}, so no SDK type is on the port's contract.
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

        // Serialising happens outside the try on purpose: a payload the mapper
        // cannot write is a bug in the caller, not the broker failing, and it
        // must not be dressed up as something a retry could fix.
        ServiceBusMessage message = new ServiceBusMessage(BinaryData.fromString(mapper.writeValueAsString(event)))
                .setContentType("application/json")
                // The broker's own duplicate detection keys on this, and so does
                // the consumer: same eventId, same fact, however many deliveries.
                .setMessageId(event.eventId().toString())
                .setSubject(type.wireName());

        try {
            sender(type).sendMessage(message);
        } catch (RuntimeException e) {
            if (!isBrokerFailure(e)) {
                throw e;
            }
            throw new EventPublishException(
                    "Failed to publish " + type.wireName() + " to queue '" + type.queue() + "'", e);
        }

        log.info("Published {} eventId={} queue={}", type.wireName(), event.eventId(), type.queue());
        return event.eventId();
    }

    /**
     * The same rule the Blob adapter applies, and for the same reason — see
     * {@code AzureBlobStorage}. Declared Azure types cover an error the broker
     * answered ({@code ServiceBusException} and {@code AmqpException} both sit
     * under {@link AzureException}); a connection that never produced an answer
     * arrives wrapped in a plain runtime exception and is recognised by its
     * cause. Anything else is a bug here and travels as itself.
     */
    private static boolean isBrokerFailure(RuntimeException e) {
        if (e instanceof AzureException || e instanceof UncheckedIOException) {
            return true;
        }
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return true;
            }
        }
        return false;
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
