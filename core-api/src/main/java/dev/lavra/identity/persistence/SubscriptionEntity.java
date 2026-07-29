package dev.lavra.identity.persistence;

import dev.lavra.identity.domain.BillingPeriod;
import dev.lavra.identity.domain.PlanCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class SubscriptionEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_code", nullable = false)
    private PlanCode planCode;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SubscriptionEntity() {
        // for JPA
    }

    public SubscriptionEntity(UUID userId, PlanCode planCode, BillingPeriod period, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.planCode = planCode;
        this.periodStart = period.start();
        this.periodEnd = period.end();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public BillingPeriod period() {
        return new BillingPeriod(periodStart, periodEnd);
    }

    /**
     * Advances the stored period until it contains {@code now}. Returns whether
     * the period actually moved, so the caller can skip a pointless write.
     */
    public boolean rollPeriodTo(Instant now) {
        BillingPeriod rolled = period().rolledTo(now);
        if (rolled.equals(period())) {
            return false;
        }
        this.periodStart = rolled.start();
        this.periodEnd = rolled.end();
        return true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public PlanCode getPlanCode() {
        return planCode;
    }
}
