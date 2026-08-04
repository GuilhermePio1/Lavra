package dev.lavra.shared.messaging;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import tools.jackson.databind.ObjectMapper;

/**
 * The {@link EventPublisher} the slice tests publish through: it keeps the
 * messages instead of sending them — and refuses any that would violate the
 * published schema.
 *
 * <p>That refusal is the point. A test that exercises a flow which publishes an
 * event gets contract validation for free, so a payload can never drift from
 * {@code contracts/events/} without some test failing, even though no broker is
 * involved.
 */
public class RecordingEventPublisher implements EventPublisher {

    /** A message as it would have gone on the wire. */
    public record Published(UUID eventId, EventType type, String json) {
    }

    private final ObjectMapper mapper = EventMapper.create();
    private final List<Published> published = new CopyOnWriteArrayList<>();
    private final Clock clock;

    public RecordingEventPublisher() {
        this(Clock.systemUTC());
    }

    public RecordingEventPublisher(Clock clock) {
        this.clock = clock;
    }

    @Override
    public UUID publish(EventType type, Object payload) {
        DomainEvent event = DomainEvent.of(type, payload, clock);
        String json = mapper.writeValueAsString(event);

        EventSchemas.assertValid(type.wireName(), json);

        published.add(new Published(event.eventId(), type, json));
        return event.eventId();
    }

    public List<Published> published() {
        return List.copyOf(published);
    }

    public List<Published> publishedOf(EventType type) {
        return published.stream().filter(message -> message.type() == type).toList();
    }

    public void clear() {
        published.clear();
    }
}
