package dev.lavra.episode.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * What the client says it is about to upload, once checked and found coherent.
 *
 * <p>Existing as a type is the point: an episode cannot be registered from a
 * filename and a content type that were never validated together, and
 * {@code upload-complete} has something precise to compare the stored blob
 * against.
 *
 * <p>The rules are the contract's ({@code EpisodeUploadRequest}) and they are
 * plain Java on purpose — no Spring is needed to check that a {@code .flac}
 * declared as {@code audio/mpeg} is refused.
 *
 * @param filename    original name, kept for the worker and the export
 * @param contentType exactly as declared; the browser stamps this on the blob,
 *                    so this — not the format's canonical type — is what the
 *                    stored blob is later compared against
 * @param format      the format both fields agree on
 * @param sizeBytes   declared size, verified against the blob at completion
 */
public record UploadDeclaration(String filename, String contentType, AudioFormat format, long sizeBytes) {

    /**
     * Ceiling of a single upload, as stated in the contract
     * ({@code EpisodeUploadRequest.sizeBytes}). Roughly 20 hours of 256 kbps
     * audio — far past any episode, and short of the block-blob limit.
     *
     * <p>A platform ceiling, not a property of any format, and not a knob: the
     * OpenAPI document publishes this exact number, so a configurable value
     * could only drift from what the contract promises. Per-plan limits, if
     * they ever exist, are minutes in the ledger (spec 0004), not bytes here.
     */
    public static final long MAX_SIZE_BYTES = 2L * 1024 * 1024 * 1024;

    /** Why a declaration was refused; decides the status the API answers with. */
    public enum Rejection {

        /** Content type outside the accepted list. */
        UNSUPPORTED_CONTENT_TYPE,

        /** Filename extension contradicts the declared content type. */
        EXTENSION_MISMATCH,

        /** Declared size is over {@link UploadDeclaration#MAX_SIZE_BYTES}. */
        TOO_LARGE
    }

    /**
     * Size is guarded here, not in {@link #of}, because a record's canonical
     * constructor is public whatever the factory does: this is the one path
     * every declaration goes through, including a caller that never saw an HTTP
     * request.
     *
     * <p>The two failures are deliberately different types. A size over the
     * ceiling is something a client can genuinely send and the contract answers
     * 413 for, so it carries a {@link Rejection}. A size at or below zero cannot
     * survive the contract ({@code minimum: 1}) or {@code @Positive} on
     * {@code EpisodeUploadRequest}, so reaching it means our own code is wrong —
     * an {@link IllegalArgumentException}, and the 500 that follows from it, is
     * the honest answer.
     *
     * @throws IllegalArgumentException          if the declared size is not positive
     * @throws InvalidUploadDeclarationException if it is over {@link #MAX_SIZE_BYTES}
     */
    public UploadDeclaration {
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(format, "format");

        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("Declared size must be positive, was " + sizeBytes);
        }
        if (sizeBytes > MAX_SIZE_BYTES) {
            throw new InvalidUploadDeclarationException(
                    Rejection.TOO_LARGE,
                    "Declared size %d exceeds the %d byte limit"
                            .formatted(sizeBytes, MAX_SIZE_BYTES));
        }
    }

    /**
     * Validates a declaration, or refuses it.
     *
     * <p>What is left here is what needs the format lookup: a content type we
     * accept, and a filename that agrees with it. Size is the constructor's,
     * above. Either way the refusal lands before an episode row and a write
     * ticket exist — the blob is never given the chance to hold two gigabytes of
     * anything.
     *
     * @throws InvalidUploadDeclarationException if the three fields do not
     *                                           describe an upload we can accept
     */
    public static UploadDeclaration of(String filename, String contentType, long sizeBytes) {
        AudioFormat format = AudioFormat.ofContentType(contentType)
                .orElseThrow(() -> new InvalidUploadDeclarationException(
                        Rejection.UNSUPPORTED_CONTENT_TYPE,
                        "Content type " + contentType + " is not an accepted audio format"));

        if (!format.matchesFilename(filename)) {
            throw new InvalidUploadDeclarationException(
                    Rejection.EXTENSION_MISMATCH,
                    "Filename %s does not end in .%s, as declared by %s"
                            .formatted(filename, format.extension(), contentType));
        }
        // Normalised, not verbatim: what gets stored is what the blob's own
        // content type is compared against later, and casing is not a
        // difference worth failing an upload over.
        return new UploadDeclaration(filename, contentType.trim().toLowerCase(Locale.ROOT), format, sizeBytes);
    }
}
