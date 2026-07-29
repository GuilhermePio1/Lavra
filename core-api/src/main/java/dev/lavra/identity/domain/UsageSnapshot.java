package dev.lavra.identity.domain;

import java.util.Objects;

/**
 * What a user has consumed in the current billing period, measured against the
 * entitlements of the plan in force.
 *
 * <p>This is where the quota rules of spec 0004 live. They are deliberately
 * plain Java: "the episode that busts the limit mid-processing still completes
 * and leaves a negative balance" has to be verifiable without a database.
 *
 * @param processedMinutesUsed may exceed the plan limit — a single overdraft is
 *                             allowed so an episode already being processed is
 *                             never aborted
 */
public record UsageSnapshot(Plan plan, BillingPeriod period, long processedMinutesUsed, long showsUsed) {

    public UsageSnapshot {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(period, "period");
        if (processedMinutesUsed < 0) {
            throw new IllegalArgumentException("processedMinutesUsed must not be negative");
        }
        if (showsUsed < 0) {
            throw new IllegalArgumentException("showsUsed must not be negative");
        }
    }

    public int processedMinutesLimit() {
        return plan.monthlyProcessedMinutes();
    }

    public int showsLimit() {
        return plan.maxShows();
    }

    /**
     * Whether a new upload may start. Checked at upload time only: once an
     * episode is processing, cost is already committed and the quota no longer
     * has a say.
     */
    public boolean hasProcessedMinutesBalance() {
        return processedMinutesUsed < processedMinutesLimit();
    }

    /** Whether the user may create one more show. */
    public boolean canCreateShow() {
        return showsUsed < showsLimit();
    }

    /** True once a debit has pushed consumption past the limit. */
    public boolean overdrawn() {
        return processedMinutesUsed > processedMinutesLimit();
    }

    /** Minutes left in the period; never negative, even when overdrawn. */
    public long remainingProcessedMinutes() {
        return Math.max(0, processedMinutesLimit() - processedMinutesUsed);
    }
}
