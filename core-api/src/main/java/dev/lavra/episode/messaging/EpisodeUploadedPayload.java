package dev.lavra.episode.messaging;

import java.util.UUID;

/**
 * The {@code data} half of {@code episode.uploaded.v1} — the message that hands
 * the episode to the media-worker (spec 0001).
 *
 * <p>Field names and types are the schema's
 * ({@code contracts/events/episode.uploaded.v1.json}), which is checked against
 * this class on every test that publishes: a rename here fails the build rather
 * than the worker.
 *
 * <p>{@code processingOptions} is not carried. It is optional in the schema and
 * omitting it means "use the defaults of spec 0002", which is the only thing the
 * API can say until per-episode overrides exist.
 *
 * @param rawAudioBlobPath container-relative path, never a URL: the worker reads
 *                         it with its own credential, not with the one the
 *                         browser used to write it
 */
public record EpisodeUploadedPayload(UUID episodeId,
                                     UUID showId,
                                     String rawAudioBlobPath,
                                     String originalFilename,
                                     String contentType) {
}
