package dev.lavra.episode.domain;

import java.util.UUID;

/**
 * Where an episode's files live in the container.
 *
 * <p>One container with prefixes, not a container per kind: {@code raw/} is what
 * the browser writes with a write ticket, {@code processed/} is what the worker
 * writes back. Keeping the layout in one place is what lets the write ticket be
 * scoped to a single path — the SAS is signed for exactly this string, so a
 * caller cannot reach another episode's audio (ADR-0011).
 */
public final class EpisodeBlobLayout {

    private EpisodeBlobLayout() {
    }

    /** Raw audio as uploaded, e.g. {@code raw/{episodeId}/original.mp3}. */
    public static String rawAudio(UUID episodeId, AudioFormat format) {
        return "raw/%s/original.%s".formatted(episodeId, format.extension());
    }
}
