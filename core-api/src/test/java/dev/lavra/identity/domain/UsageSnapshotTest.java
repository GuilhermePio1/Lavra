package dev.lavra.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Quota rules of spec 0004. These are the cases the episode slice will lean on,
 * so they are pinned down here — with no database and no Spring context.
 */
class UsageSnapshotTest {

    private static final Plan FREE = new Plan(PlanCode.FREE, "Free", 90, 1);
    private static final BillingPeriod PERIOD =
            BillingPeriod.monthlyStartingAt(Instant.parse("2026-01-15T10:00:00Z"));

    private static UsageSnapshot usage(long minutesUsed, long showsUsed) {
        return new UsageSnapshot(FREE, PERIOD, minutesUsed, showsUsed);
    }

    @Nested
    @DisplayName("processed-minutes balance")
    class ProcessedMinutes {

        @Test
        @DisplayName("an upload is allowed while consumption is below the limit")
        void allowsUploadBelowLimit() {
            assertThat(usage(89, 0).hasProcessedMinutesBalance()).isTrue();
        }

        @Test
        @DisplayName("hitting the limit exactly blocks the next upload")
        void blocksUploadAtLimit() {
            assertThat(usage(90, 0).hasProcessedMinutesBalance()).isFalse();
        }

        @Test
        @DisplayName("a negative balance from an overdraft blocks new uploads")
        void blocksUploadWhenOverdrawn() {
            UsageSnapshot overdrawn = usage(120, 0);

            assertThat(overdrawn.hasProcessedMinutesBalance()).isFalse();
            assertThat(overdrawn.overdrawn()).isTrue();
        }

        @Test
        @DisplayName("consumption may exceed the limit: the episode in flight is never aborted")
        void allowsConsumptionPastTheLimit() {
            assertThat(usage(120, 0).processedMinutesUsed()).isEqualTo(120);
            assertThat(usage(120, 0).processedMinutesLimit()).isEqualTo(90);
        }

        @Test
        @DisplayName("remaining minutes floor at zero instead of going negative")
        void remainingNeverGoesNegative() {
            assertThat(usage(30, 0).remainingProcessedMinutes()).isEqualTo(60);
            assertThat(usage(120, 0).remainingProcessedMinutes()).isZero();
        }

        @Test
        @DisplayName("exactly at the limit is not yet an overdraft")
        void limitReachedIsNotOverdraft() {
            assertThat(usage(90, 0).overdrawn()).isFalse();
        }
    }

    @Nested
    @DisplayName("show limit")
    class Shows {

        @Test
        @DisplayName("the first show fits in FREE")
        void allowsFirstShow() {
            assertThat(usage(0, 0).canCreateShow()).isTrue();
        }

        @Test
        @DisplayName("the second show does not — a distinct condition from the minute quota")
        void blocksSecondShow() {
            UsageSnapshot atShowLimit = usage(0, 1);

            assertThat(atShowLimit.canCreateShow()).isFalse();
            assertThat(atShowLimit.hasProcessedMinutesBalance()).isTrue();
        }

        @Test
        @DisplayName("STUDIO raises the ceiling without touching the code")
        void limitComesFromThePlan() {
            Plan studio = new Plan(PlanCode.STUDIO, "Studio", 1800, 3);

            assertThat(new UsageSnapshot(studio, PERIOD, 0, 2).canCreateShow()).isTrue();
            assertThat(new UsageSnapshot(studio, PERIOD, 0, 3).canCreateShow()).isFalse();
        }
    }

    @Test
    @DisplayName("every new user starts on FREE")
    void defaultPlanIsFree() {
        assertThat(PlanCode.defaultForNewUser()).isEqualTo(PlanCode.FREE);
    }
}
