package dev.lavra.show.persistence;

import dev.lavra.show.domain.VoiceProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A podcast: the aggregate that owns episodes and carries the voice profile.
 *
 * <p>Travels as-is up to the web layer instead of being mapped onto a separate
 * domain record — ADR-0013 allows that for aggregates whose only real rule
 * ({@link VoiceProfile}) already lives in its own domain type.
 */
@Entity
@Table(name = "shows")
public class ShowEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    /**
     * A document, not a table: it is only ever read and written whole, together
     * with the show that owns it, and its examples array grows without a fixed
     * shape (spec 0003).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "voice_profile", nullable = false)
    private VoiceProfile voiceProfile;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ShowEntity() {
        // for JPA
    }

    public ShowEntity(UUID userId, String name, String description, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.voiceProfile = VoiceProfile.empty();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Applies a partial update: a null field was not sent and stays as it is.
     * Clearing the description is done by sending an empty string.
     */
    public void applyPatch(String name, String description) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
    }

    public void replaceEditableVoiceProfile(String toneDescription, List<String> antiExamples, Instant now) {
        this.voiceProfile = voiceProfile().withEditableParts(toneDescription, antiExamples, now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /** Never null to the caller: a show stored before any edit reads as empty. */
    public VoiceProfile voiceProfile() {
        return voiceProfile == null ? VoiceProfile.empty() : voiceProfile;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
