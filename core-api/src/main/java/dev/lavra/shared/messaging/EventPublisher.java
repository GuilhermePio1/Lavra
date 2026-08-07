package dev.lavra.shared.messaging;

import java.util.UUID;

/**
 * Port to the message broker (ADR-0006, ADR-0013). Publishing is what hands work
 * to the media-worker; the state machine itself stays in Postgres, which remains
 * the source of truth.
 *
 * <p>As with {@link dev.lavra.shared.blob.BlobStorage}, failures arrive as a
 * type of this package and never as an SDK type (ADR-0015).
 */
public interface EventPublisher {

    /**
     * Wraps the payload in the standard envelope and sends it to the queue of
     * its type.
     *
     * @param type    which event this is; decides schema and queue
     * @param payload the {@code data} half of the message, shaped by the schema
     * @return the generated {@code eventId}, for logging and correlation
     * @throws EventPublishException if the broker cannot be reached or refuses
     *                               the message
     */
    UUID publish(EventType type, Object payload);
}
