package dev.lavra.episode.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * The episode's place in the pipeline, and the only transitions the pipeline
 * allows (spec 0001).
 *
 * <p>Postgres is the source of truth for the state, but the rule about which
 * move is legal lives here: plain Java, no Spring, so "a failed episode may go
 * back to processing but never straight to review" is verifiable in a unit
 * test.
 *
 * <p>The worker does not know about states at all — it consumes and publishes
 * events, and the Core API is what turns those into transitions.
 */
public enum EpisodeStatus {

    /** Registered; the browser is uploading the raw audio to the blob. */
    PENDING_UPLOAD,

    /** Raw audio is in the blob and confirmed. */
    RECEIVED,

    /** Worker is cleaning and normalising the audio (spec 0002). */
    AUDIO_PROCESSING,

    /** Worker is transcribing (ADR-0008). */
    TRANSCRIBING,

    /** Core is generating titles, description and chapters (spec 0003). */
    GENERATING,

    /** Content is ready and waiting for the creator's review. */
    IN_REVIEW,

    /** Approved by the creator; artefacts may be exported. */
    READY,

    /** Unrecoverable error at some stage; carries stage, code and message. */
    FAILED;

    /**
     * States a running pipeline can fail from. {@code PENDING_UPLOAD} is not one
     * of them: nothing is being processed yet, and an upload nobody finishes is
     * swept away by the cleanup job rather than marked failed.
     */
    private static final Set<EpisodeStatus> FAILABLE =
            EnumSet.of(RECEIVED, AUDIO_PROCESSING, TRANSCRIBING, GENERATING);

    /**
     * Whether the pipeline may move an episode from this state to {@code next}.
     *
     * <p>Beyond the straight line of the spec, only two moves exist: any stage
     * that is actually processing may fail, and a failed episode may be
     * reprocessed by re-publishing {@code episode.uploaded} — which puts it back
     * in {@code AUDIO_PROCESSING}, not at the start.
     *
     * <p>Reprocessing restarts the pipeline instead of resuming at the stage
     * that failed, deliberately: {@code media.processed.v1} is published only
     * once cleanup and transcription have both succeeded, so no event resumes a
     * worker job halfway — a pointwise retry would need a new contract. The
     * price is that a failure after transcription pays for transcription again,
     * and since the ledger is unique by (episode, period) the platform pays it,
     * not the creator. Accepted for the MVP; reopening it is a change to spec
     * 0001, and the resume point would be chosen in the service, from
     * {@code failed_stage} — this enum only ever sees {@code FAILED}.
     */
    public boolean canTransitionTo(EpisodeStatus next) {
        if (next == FAILED) {
            return FAILABLE.contains(this);
        }
        return switch (this) {
            case PENDING_UPLOAD -> next == RECEIVED;
            case RECEIVED -> next == AUDIO_PROCESSING;
            case AUDIO_PROCESSING -> next == TRANSCRIBING;
            case TRANSCRIBING -> next == GENERATING;
            case GENERATING -> next == IN_REVIEW;
            case IN_REVIEW -> next == READY;
            case READY -> false;
            case FAILED -> next == AUDIO_PROCESSING;
        };
    }

    /** Whether the raw audio is still on its way. */
    public boolean isAwaitingUpload() {
        return this == PENDING_UPLOAD;
    }
}
