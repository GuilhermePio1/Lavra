package dev.lavra.show.web;

import dev.lavra.show.ShowWithEpisodeCount;
import dev.lavra.show.persistence.ShowEntity;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code Show} of the REST contract
 * ({@code contracts/openapi/core-api.v1.yaml}).
 */
record ShowResponse(UUID id,
                    String name,
                    String description,
                    long episodeCount,
                    Instant createdAt,
                    Instant updatedAt) {

    static ShowResponse from(ShowWithEpisodeCount result) {
        ShowEntity show = result.show();
        return new ShowResponse(
                show.getId(),
                show.getName(),
                show.getDescription(),
                result.episodeCount(),
                show.getCreatedAt(),
                show.getUpdatedAt());
    }
}
