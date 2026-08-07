package dev.lavra.episode.persistence;

import dev.lavra.episode.domain.EpisodeBlobLayout;
import dev.lavra.episode.domain.EpisodeStatus;
import dev.lavra.episode.domain.IllegalEpisodeTransitionException;
import dev.lavra.episode.domain.UploadDeclaration;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An episode: the unit the whole pipeline moves through.
 *
 * <p>Holds what the client declared at upload time — filename, content type and
 * size — because {@code upload-complete} has to compare the blob that actually
 * landed against it, and {@code episode.uploaded.v1} carries the first two to
 * the worker.
 */
@Entity
@Table(name = "episodes")
public class EpisodeEntity {

    @Id
    private UUID id;

    @Column(name = "show_id", nullable = false, updatable = false)
    private UUID showId;

    /** The creator's own title; the AI will suggest others during review. */
    @Column(name = "working_title")
    private String workingTitle;

    /**
     * Stored as text, not an ordinal: the column has a check constraint listing
     * the names, and the events and the API speak the same names. An ordinal
     * would make inserting a state in the middle of the enum a data migration.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EpisodeStatus status;

    @Column(name = "source_blob_path")
    private String sourceBlobPath;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Filled by the worker's report; null until the audio has been measured. */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "failed_stage")
    private String failedStage;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EpisodeEntity() {
        // for JPA
    }

    /**
     * Registers an episode that is waiting for its audio. The blob path is
     * derived here rather than taken from the request: it is the path the write
     * ticket will be signed for, and letting a caller influence it would let
     * them aim the credential somewhere else.
     */
    public EpisodeEntity(UUID showId, String workingTitle, UploadDeclaration declaration, Instant now) {
        this.id = UUID.randomUUID();
        this.showId = showId;
        this.workingTitle = workingTitle;
        this.status = EpisodeStatus.PENDING_UPLOAD;
        this.sourceBlobPath = EpisodeBlobLayout.rawAudio(this.id, declaration.format());
        this.originalFilename = declaration.filename();
        this.contentType = declaration.contentType();
        this.sizeBytes = declaration.sizeBytes();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Moves the episode along the state machine, refusing any move the domain
     * does not allow. The caller is responsible for recording the transition in
     * the history table — the entity cannot write another one.
     */
    public void transitionTo(EpisodeStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalEpisodeTransitionException(id, status, next);
        }
        this.status = next;
    }

    /**
     * Whether the blob that landed is the one the client said it would upload.
     * Size and type both have to match: a truncated upload has the right type,
     * and a client that streams something else entirely has the right size only
     * by coincidence.
     */
    public boolean matchesDeclaration(long actualSizeBytes, String actualContentType) {
        return sizeBytes == actualSizeBytes && contentType.equalsIgnoreCase(actualContentType);
    }

    public UUID getId() {
        return id;
    }

    public UUID getShowId() {
        return showId;
    }

    public String getWorkingTitle() {
        return workingTitle;
    }

    public EpisodeStatus getStatus() {
        return status;
    }

    public String getSourceBlobPath() {
        return sourceBlobPath;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public String getFailedStage() {
        return failedStage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
