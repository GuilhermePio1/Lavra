package dev.lavra.episode;

import dev.lavra.episode.domain.EpisodeStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * An episode as the list endpoint shows it — the {@code Episode} schema of the
 * REST contract, and nothing more.
 *
 * <p>A projection rather than the entity: a list of a hundred episodes has no
 * use for blob paths, declarations or error details, and not loading them is
 * also what keeps them from leaking into a payload by accident.
 */
public record EpisodeSummary(UUID id,
                             String workingTitle,
                             EpisodeStatus status,
                             Integer durationSeconds,
                             Instant createdAt,
                             Instant updatedAt) {
}
