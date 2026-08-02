package dev.lavra.show.domain;

import java.time.Instant;
import java.util.List;

/**
 * How a show sounds: the calibration that makes generated titles read like the
 * creator instead of like an LLM (spec 0003).
 *
 * <p>The profile has two halves with different owners, and keeping them apart is
 * the rule this type exists to enforce. <strong>Tone and anti-examples</strong>
 * are edited by the creator through the API. <strong>Examples</strong> are not:
 * they accumulate on their own, one per episode approved in
 * {@code IN_REVIEW → READY}, and no request may erase them — which is why
 * {@link #withEditableParts} rewrites everything else and leaves them alone.
 *
 * <p>Stored as a JSON document on the show. Absent fields read back as empty
 * rather than null, so a show created before the profile was ever touched
 * behaves like one with a blank profile.
 *
 * @param updatedAt when the editable part last changed; null while untouched
 */
public record VoiceProfile(String toneDescription,
                           List<String> antiExamples,
                           List<VoiceExample> examples,
                           Instant updatedAt) {

    public VoiceProfile {
        toneDescription = toneDescription == null ? "" : toneDescription;
        antiExamples = antiExamples == null ? List.of() : List.copyOf(antiExamples);
        examples = examples == null ? List.of() : List.copyOf(examples);
    }

    /** The profile a show is born with. */
    public static VoiceProfile empty() {
        return new VoiceProfile("", List.of(), List.of(), null);
    }

    /**
     * Replaces the creator-editable half wholesale — PUT semantics, per the
     * contract — while carrying the accumulated examples across untouched.
     */
    public VoiceProfile withEditableParts(String toneDescription, List<String> antiExamples, Instant now) {
        return new VoiceProfile(toneDescription, antiExamples, examples, now);
    }
}
