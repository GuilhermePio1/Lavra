package dev.lavra.episode.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.lavra.episode.EpisodeSummary;
import dev.lavra.episode.domain.EpisodeStatus;
import dev.lavra.episode.persistence.EpisodeEntity;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code Episode} of the REST contract
 * ({@code contracts/openapi/core-api.v1.yaml}).
 *
 * <p>Carries no blob path and no upload declaration: where the audio lives is
 * the API's business, and the client already knows what it sent.
 *
 * <p>Null fields are left out rather than sent as {@code null}, as in
 * {@link EpisodeDetailResponse}: an episode with no working title and one whose
 * duration nobody has measured yet should read the same way on every route that
 * returns it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record EpisodeResponse(UUID id,
                       String workingTitle,
                       EpisodeStatus status,
                       Integer durationSeconds,
                       Instant createdAt,
                       Instant updatedAt) {

    static EpisodeResponse from(EpisodeEntity episode) {
        return new EpisodeResponse(
                episode.getId(),
                episode.getWorkingTitle(),
                episode.getStatus(),
                episode.getDurationSeconds(),
                episode.getCreatedAt(),
                episode.getUpdatedAt());
    }

    static EpisodeResponse from(EpisodeSummary summary) {
        return new EpisodeResponse(
                summary.id(),
                summary.workingTitle(),
                summary.status(),
                summary.durationSeconds(),
                summary.createdAt(),
                summary.updatedAt());
    }
}
