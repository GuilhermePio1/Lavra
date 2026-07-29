package dev.lavra.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    /** The {@code oid} (or {@code sub}) claim of the OIDC token. */
    @Column(name = "external_id", nullable = false, updatable = false)
    private String externalId;

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
        // for JPA
    }

    public UserEntity(String externalId, String email, String displayName, Instant now) {
        this.id = UUID.randomUUID();
        this.externalId = externalId;
        this.email = email;
        this.displayName = displayName;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Keeps the local record in step with the token, which is the source of
     * truth for name and email. Returns whether anything actually changed.
     */
    public boolean refreshProfile(String email, String displayName) {
        boolean changed = false;
        if (email != null && !email.equals(this.email)) {
            this.email = email;
            changed = true;
        }
        if (displayName != null && !displayName.equals(this.displayName)) {
            this.displayName = displayName;
            changed = true;
        }
        return changed;
    }

    public UUID getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
