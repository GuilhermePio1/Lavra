package dev.lavra.shared.messaging;

/**
 * An event could not be handed to the broker: the namespace is unreachable, the
 * credential was refused, the queue does not exist.
 *
 * <p>The counterpart of {@link dev.lavra.shared.blob.BlobAccessException}, and
 * for the same reason: a caller should not need {@code com.azure} on its
 * imports to reason about publishing failing (ADR-0015).
 *
 * <p>Note what it does <em>not</em> cover. A payload that cannot be serialised
 * fails before any broker is involved and is a bug in the caller, not the
 * broker misbehaving — it escapes as itself, so that a retry policy keyed on
 * this type can never spend attempts on something that will fail identically
 * every time.
 */
public class EventPublishException extends RuntimeException {

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
