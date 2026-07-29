package dev.lavra.identity.domain;

import java.util.Objects;

/**
 * A plan and the entitlements it grants. Read from the catalogue on every
 * request — entitlements are never cached across a plan change (spec 0004).
 *
 * @param monthlyProcessedMinutes minutes of audio the user may process per
 *                                billing period; the quota tracks processed
 *                                minutes because that is what actually costs
 * @param maxShows                how many shows the user may own at once
 */
public record Plan(PlanCode code, String name, int monthlyProcessedMinutes, int maxShows) {

    public Plan {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        if (monthlyProcessedMinutes < 0) {
            throw new IllegalArgumentException("monthlyProcessedMinutes must not be negative");
        }
        if (maxShows < 0) {
            throw new IllegalArgumentException("maxShows must not be negative");
        }
    }
}
