package dev.lavra.episode.persistence;

import dev.lavra.episode.domain.EpisodeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * One move of an episode through the pipeline, kept forever.
 *
 * <p>This is the history the tracking UI reads and the first thing worth
 * looking at when an episode ends up somewhere unexpected — which is why it is
 * a table of facts rather than a column that gets overwritten (spec 0001).
 *
 * <p>Not a collection on {@link EpisodeEntity}: the list endpoint has no use for
 * transitions, and mapping them as one would either load them everywhere or
 * make the detail route depend on an open session, which
 * {@code open-in-view: false} does not give it (ADR-0014).
 *
 * <p>{@code @Immutable} says at the mapping level what "a fact" means: Hibernate
 * keeps no dirty-check snapshot of these rows and will not update them, so a
 * setter added here in a year would be inert rather than quietly rewriting
 * history. It guards the session, not the table — raw SQL still reaches the
 * rows, and only a trigger or a withheld {@code update} grant would stop that.
 */
@Entity
@Immutable
@Table(name = "episode_state_transitions")
public class EpisodeStateTransitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    /**
     * Where the episode came from. Never null: an episode's beginning is its own
     * row in {@code episodes}, not a transition out of nothing, and the history
     * payload of the contract requires both ends of every move.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    private EpisodeStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private EpisodeStatus toStatus;

    /** What caused the move, in English, for the log and the debugger. */
    @Column
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected EpisodeStateTransitionEntity() {
        // for JPA
    }

    /**
     * Every column the schema declares {@code not null} is checked here, so a
     * fact that could not be stored fails where it was built rather than at the
     * flush, with the stack trace pointing at the transition that was missing a
     * piece. {@code reason} is the one field left open — a move that explains
     * itself is allowed not to.
     */
    public EpisodeStateTransitionEntity(UUID episodeId,
                                        EpisodeStatus fromStatus,
                                        EpisodeStatus toStatus,
                                        String reason,
                                        Instant occurredAt) {
        this.episodeId = Objects.requireNonNull(episodeId, "episodeId");
        this.fromStatus = Objects.requireNonNull(fromStatus, "fromStatus");
        this.toStatus = Objects.requireNonNull(toStatus, "toStatus");
        this.reason = reason;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public UUID getEpisodeId() {
        return episodeId;
    }

    public EpisodeStatus getFromStatus() {
        return fromStatus;
    }

    public EpisodeStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
