package dev.lavra.show.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One approved episode used as few-shot material for generation (spec 0003).
 *
 * <p>Holds the text the creator actually approved — edits included — because the
 * system is meant to learn from the correction, not from its own output.
 *
 * @param episodeId   the episode this text was approved on
 * @param description empty when the creator approved a title but no description
 * @param approvedAt  when it moved to {@code READY}
 */
public record VoiceExample(UUID episodeId, String title, String description, Instant approvedAt) {

    public VoiceExample {
        Objects.requireNonNull(episodeId, "episodeId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(approvedAt, "approvedAt");
        description = description == null ? "" : description;
    }
}
