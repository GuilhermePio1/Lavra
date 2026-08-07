package dev.lavra.episode.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.lavra.episode.EpisodeWithHistory;
import dev.lavra.episode.domain.EpisodeStatus;
import dev.lavra.episode.persistence.EpisodeEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code EpisodeDetail} of the REST contract: the episode, why it failed if it
 * did, and every move it has made.
 *
 * <p>Null fields are left out rather than sent as {@code null} — the contract
 * says the failure block is present only for a failed episode, and an absent key
 * says that more plainly than a null one.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record EpisodeDetailResponse(UUID id,
                             String workingTitle,
                             EpisodeStatus status,
                             Integer durationSeconds,
                             Instant createdAt,
                             Instant updatedAt,
                             Failure failure,
                             List<Transition> transitions) {

    /**
     * @param retryable whether the pipeline can be started again from here,
     *                  asked of the state machine rather than stored: it is the
     *                  same rule that would refuse the move
     */
    record Failure(String stage, String errorCode, String errorMessage, boolean retryable) {
    }

    record Transition(EpisodeStatus fromStatus, EpisodeStatus toStatus, Instant at) {
    }

    static EpisodeDetailResponse from(EpisodeWithHistory detail) {
        EpisodeEntity episode = detail.episode();
        return new EpisodeDetailResponse(
                episode.getId(),
                episode.getWorkingTitle(),
                episode.getStatus(),
                episode.getDurationSeconds(),
                episode.getCreatedAt(),
                episode.getUpdatedAt(),
                failureOf(episode),
                detail.transitions().stream()
                        .map(transition -> new Transition(
                                transition.getFromStatus(),
                                transition.getToStatus(),
                                transition.getOccurredAt()))
                        .toList());
    }

    private static Failure failureOf(EpisodeEntity episode) {
        if (episode.getStatus() != EpisodeStatus.FAILED) {
            return null;
        }
        return new Failure(
                episode.getFailedStage(),
                episode.getErrorCode(),
                episode.getErrorMessage(),
                EpisodeStatus.FAILED.canTransitionTo(EpisodeStatus.AUDIO_PROCESSING));
    }
}
