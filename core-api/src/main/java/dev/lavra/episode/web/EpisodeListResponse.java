package dev.lavra.episode.web;

import dev.lavra.episode.EpisodePage;
import java.util.List;

/**
 * The paginated payload of {@code GET /episodes}. Unlike shows, episodes have no
 * ceiling worth relying on — a studio account accumulates them indefinitely, so
 * this list is paged from the start.
 */
record EpisodeListResponse(List<EpisodeResponse> items, int page, long totalItems) {

    static EpisodeListResponse from(EpisodePage page) {
        return new EpisodeListResponse(
                page.items().stream().map(EpisodeResponse::from).toList(),
                page.page(),
                page.totalItems());
    }
}
