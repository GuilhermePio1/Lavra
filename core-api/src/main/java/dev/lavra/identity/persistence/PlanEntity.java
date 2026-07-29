package dev.lavra.identity.persistence;

import dev.lavra.identity.domain.Plan;
import dev.lavra.identity.domain.PlanCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Read-only view of the plan catalogue. Limits are data: changing one is an
 * UPDATE, not a deploy (spec 0004).
 */
@Entity
@Table(name = "plans")
public class PlanEntity {

    @Id
    @Enumerated(EnumType.STRING)
    private PlanCode code;

    @Column(nullable = false)
    private String name;

    @Column(name = "monthly_processed_minutes", nullable = false)
    private int monthlyProcessedMinutes;

    @Column(name = "max_shows", nullable = false)
    private int maxShows;

    protected PlanEntity() {
        // for JPA
    }

    public Plan toDomain() {
        return new Plan(code, name, monthlyProcessedMinutes, maxShows);
    }

    public PlanCode getCode() {
        return code;
    }
}
