package dev.lavra.shared.messaging;

/**
 * The events this service publishes, each bound to the queue that carries it.
 *
 * <p>{@code wireName} is the contract: it is the {@code eventType} constant of
 * the message and the name of the schema file in {@code contracts/events/}. A
 * breaking change to a payload never edits an entry here — it adds one for
 * {@code vN+1}, because a published schema is immutable.
 */
public enum EventType {

    /** Raw audio is in the blob and the pipeline may start (spec 0001). */
    EPISODE_UPLOADED_V1("episode.uploaded.v1", "episode-uploaded");

    private final String wireName;
    private final String queue;

    EventType(String wireName, String queue) {
        this.wireName = wireName;
        this.queue = queue;
    }

    public String wireName() {
        return wireName;
    }

    public String queue() {
        return queue;
    }
}
