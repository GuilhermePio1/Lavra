package dev.lavra.episode;

import dev.lavra.episode.persistence.EpisodeEntity;
import dev.lavra.episode.persistence.EpisodeStateTransitionEntity;
import java.util.List;

/**
 * An episode plus every move it has made — what {@code EpisodeDetail} of the
 * contract shows: where the episode is now, and how it got there.
 *
 * <p>Fetched only by the detail route. The history is not part of the episode's
 * state, it is the record of it, and the list endpoint has no use for it.
 */
public record EpisodeWithHistory(EpisodeEntity episode, List<EpisodeStateTransitionEntity> transitions) {
}
