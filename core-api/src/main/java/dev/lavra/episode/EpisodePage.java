package dev.lavra.episode;

import java.util.List;

/**
 * One page of episodes plus the total behind it — the payload shape
 * {@code GET /episodes} promises in the contract.
 *
 * <p>{@code totalItems} counts everything the filter matches, not what came
 * back in {@code items}: it is what the frontend needs to render the pager.
 */
public record EpisodePage(List<EpisodeSummary> items, int page, long totalItems) {
}
