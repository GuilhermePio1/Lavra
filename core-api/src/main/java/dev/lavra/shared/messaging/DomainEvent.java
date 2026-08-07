package dev.lavra.shared.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * The envelope every message on the bus shares, as defined by the schemas in
 * {@code contracts/events/}: identity, type, time and a typed payload.
 *
 * @param eventId    unique per message; the consumer deduplicates on it, which
 *                   is what makes at-least-once delivery survivable
 * @param eventType  the {@link EventType#wireName()} of this message
 * @param occurredAt when the fact happened, UTC
 * @param data       the payload, shaped by the schema of this event type
 */
public record DomainEvent(UUID eventId, String eventType, Instant occurredAt, Object data) {

    /**
     * The one place an envelope is built, so the messages the tests check and
     * the messages the broker receives cannot be shaped differently.
     */
    static DomainEvent of(EventType type, Object payload, Clock clock) {
        return new DomainEvent(UUID.randomUUID(), type.wireName(), clock.instant(), payload);
    }
}
