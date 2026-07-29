package dev.lavra.identity.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * The half-open interval {@code [start, end)} a subscription is currently in.
 *
 * <p>Roll-over is a simple monthly advance anchored on the subscription start:
 * unused minutes do not carry over (spec 0004). A period that starts on the
 * 31st lands on the 28th/30th in shorter months and stays there — the drift is
 * accepted, since the quota is monthly and never pro-rated.
 */
public record BillingPeriod(Instant start, Instant end) {

    public BillingPeriod {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("period end must be after start");
        }
    }

    /** The first monthly period of a subscription created at {@code start}. */
    public static BillingPeriod monthlyStartingAt(Instant start) {
        return new BillingPeriod(start, plusOneMonth(start));
    }

    public boolean contains(Instant moment) {
        return !moment.isBefore(start) && moment.isBefore(end);
    }

    /**
     * This period if it still contains {@code moment}, otherwise the monthly
     * period that does. Advancing month by month (rather than jumping straight
     * to {@code moment}) keeps the billing day stable across a gap of several
     * idle months — stable up to the drift documented on the type, which a
     * short month makes permanent.
     */
    public BillingPeriod rolledTo(Instant moment) {
        BillingPeriod current = this;
        while (!current.contains(moment)) {
            if (moment.isBefore(current.start)) {
                throw new IllegalArgumentException("cannot roll a billing period backwards");
            }
            current = new BillingPeriod(current.end, plusOneMonth(current.end));
        }
        return current;
    }

    private static Instant plusOneMonth(Instant moment) {
        return ZonedDateTime.ofInstant(moment, ZoneOffset.UTC).plusMonths(1).toInstant();
    }
}
