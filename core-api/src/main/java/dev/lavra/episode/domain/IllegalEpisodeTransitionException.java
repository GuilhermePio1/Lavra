package dev.lavra.episode.domain;

import java.util.UUID;

/**
 * Raised when something tries to move an episode along a path the state machine
 * does not have.
 *
 * <p>Deliberately unmapped by the API's exception handler: reaching it is a bug,
 * not a situation a client can be in. Every route that could legitimately meet a
 * wrong state — renewing a ticket for an episode that already uploaded, for
 * instance — checks the state first and answers 409 with a code the frontend can
 * act on. If this one ever surfaces as a 500, the guard above it is missing.
 *
 * <p>The episode's id is in the message because that is the first thing the
 * postmortem needs and the last thing the log would otherwise have: the
 * transition can be attempted while consuming a Service Bus message, where there
 * is no request URI to fall back on.
 */
public class IllegalEpisodeTransitionException extends RuntimeException {

    public IllegalEpisodeTransitionException(UUID episodeId, EpisodeStatus from, EpisodeStatus to) {
        super("Episode " + episodeId + " cannot move from " + from + " to " + to);
    }
}
