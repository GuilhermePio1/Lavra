package dev.lavra.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Billing period rules — plain JUnit, no Spring context (ADR-0013). */
class BillingPeriodTest {

    private static final Instant JAN_15 = Instant.parse("2026-01-15T10:00:00Z");

    @Test
    @DisplayName("a new subscription gets a one-month window")
    void monthlyStartingAtSpansOneMonth() {
        BillingPeriod period = BillingPeriod.monthlyStartingAt(JAN_15);

        assertThat(period.start()).isEqualTo(JAN_15);
        assertThat(period.end()).isEqualTo(Instant.parse("2026-02-15T10:00:00Z"));
    }

    @Test
    @DisplayName("the interval is half-open: start is inside, end is not")
    void containsIsHalfOpen() {
        BillingPeriod period = BillingPeriod.monthlyStartingAt(JAN_15);

        assertThat(period.contains(JAN_15)).isTrue();
        assertThat(period.contains(Instant.parse("2026-02-01T00:00:00Z"))).isTrue();
        assertThat(period.contains(period.end())).isFalse();
        assertThat(period.contains(JAN_15.minusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("a period that still contains the moment does not move")
    void rollingWithinThePeriodIsANoop() {
        BillingPeriod period = BillingPeriod.monthlyStartingAt(JAN_15);

        assertThat(period.rolledTo(Instant.parse("2026-02-14T23:59:59Z"))).isEqualTo(period);
    }

    @Test
    @DisplayName("an expired period advances to the one containing now")
    void rollsForwardOneMonth() {
        BillingPeriod rolled = BillingPeriod.monthlyStartingAt(JAN_15)
                .rolledTo(Instant.parse("2026-02-20T08:00:00Z"));

        assertThat(rolled.start()).isEqualTo(Instant.parse("2026-02-15T10:00:00Z"));
        assertThat(rolled.end()).isEqualTo(Instant.parse("2026-03-15T10:00:00Z"));
    }

    @Test
    @DisplayName("months of inactivity keep the billing day instead of jumping to today")
    void rollsMonthByMonthAcrossAGap() {
        BillingPeriod rolled = BillingPeriod.monthlyStartingAt(JAN_15)
                .rolledTo(Instant.parse("2026-06-02T00:00:00Z"));

        assertThat(rolled.start()).isEqualTo(Instant.parse("2026-05-15T10:00:00Z"));
        assertThat(rolled.end()).isEqualTo(Instant.parse("2026-06-15T10:00:00Z"));
        assertThat(rolled.contains(Instant.parse("2026-06-02T00:00:00Z"))).isTrue();
    }

    /**
     * The drift of a short month is permanent: each roll starts from the
     * previous end, never from the subscription anchor, so the billing day
     * ratchets down and does not climb back to the 31st.
     */
    @Test
    @DisplayName("a 31st anchor lands on the 28th in February and stays there")
    void driftOfAShortMonthIsPermanent() {
        BillingPeriod first = BillingPeriod.monthlyStartingAt(Instant.parse("2026-01-31T10:00:00Z"));

        assertThat(first.end()).isEqualTo(Instant.parse("2026-02-28T10:00:00Z"));
        // Consequence accepted by spec 0004: the quota window is 28 days here,
        // and the reset comes early. Monthly is never pro-rated.
        assertThat(Duration.between(first.start(), first.end()).toDays()).isEqualTo(28);

        BillingPeriod second = first.rolledTo(Instant.parse("2026-03-10T00:00:00Z"));
        assertThat(second.start()).isEqualTo(Instant.parse("2026-02-28T10:00:00Z"));
        assertThat(second.end()).isEqualTo(Instant.parse("2026-03-28T10:00:00Z"));

        BillingPeriod third = second.rolledTo(Instant.parse("2026-04-10T00:00:00Z"));
        assertThat(third.start()).isEqualTo(Instant.parse("2026-03-28T10:00:00Z"));
        assertThat(third.end()).isEqualTo(Instant.parse("2026-04-28T10:00:00Z"));
    }

    /** The landing day follows the calendar, so a leap February keeps the 29th. */
    @Test
    @DisplayName("in a leap year the same anchor lands on the 29th instead")
    void driftFollowsTheLeapYear() {
        BillingPeriod first = BillingPeriod.monthlyStartingAt(Instant.parse("2028-01-31T10:00:00Z"));

        assertThat(first.end()).isEqualTo(Instant.parse("2028-02-29T10:00:00Z"));

        BillingPeriod second = first.rolledTo(Instant.parse("2028-03-05T00:00:00Z"));
        assertThat(second.start()).isEqualTo(Instant.parse("2028-02-29T10:00:00Z"));
        assertThat(second.end()).isEqualTo(Instant.parse("2028-03-29T10:00:00Z"));
    }

    @Test
    @DisplayName("a 30-day month clamps to the 30th, and the 31st is not recovered")
    void driftOfAThirtyDayMonthIsPermanent() {
        BillingPeriod first = BillingPeriod.monthlyStartingAt(Instant.parse("2026-03-31T10:00:00Z"));

        assertThat(first.end()).isEqualTo(Instant.parse("2026-04-30T10:00:00Z"));

        BillingPeriod second = first.rolledTo(Instant.parse("2026-05-10T00:00:00Z"));
        assertThat(second.start()).isEqualTo(Instant.parse("2026-04-30T10:00:00Z"));
        assertThat(second.end()).isEqualTo(Instant.parse("2026-05-30T10:00:00Z"));
    }

    @Test
    @DisplayName("a period never rolls backwards")
    void rollingBackwardsIsRejected() {
        BillingPeriod period = BillingPeriod.monthlyStartingAt(JAN_15);

        assertThatThrownBy(() -> period.rolledTo(Instant.parse("2025-12-01T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("end must be after start")
    void rejectsInvertedInterval() {
        assertThatThrownBy(() -> new BillingPeriod(JAN_15, JAN_15))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
