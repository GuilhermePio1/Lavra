package dev.lavra.identity.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * The authenticated user as the rest of the application sees them: local
 * identity plus the plan and consumption in force right now.
 */
public record Account(UUID id, String email, String displayName, UsageSnapshot usage) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(usage, "usage");
    }

    public Plan plan() {
        return usage.plan();
    }

    public BillingPeriod period() {
        return usage.period();
    }
}
