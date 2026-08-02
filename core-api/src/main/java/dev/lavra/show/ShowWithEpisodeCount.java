package dev.lavra.show;

import dev.lavra.show.persistence.ShowEntity;

/**
 * A show plus the number of episodes it holds — what every show payload of the
 * contract carries. The count is not on the entity because it is not the show's
 * state: it is an aggregate over another table, answered per request.
 */
public record ShowWithEpisodeCount(ShowEntity show, long episodeCount) {
}
