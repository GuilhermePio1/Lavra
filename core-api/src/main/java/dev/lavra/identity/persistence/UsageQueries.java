package dev.lavra.identity.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Consumption aggregates. Native SQL on purpose (ADR-0014): the ledger is
 * summed, never mapped — loading every debit entry to add them up in Java would
 * be wasteful and would tempt someone into caching a counter.
 */
@Repository
public class UsageQueries {

    private final JdbcClient jdbcClient;

    UsageQueries(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Minutes debited to the user within the given period. Counts only debits
     * stamped with this period's start, so a period roll-over resets
     * consumption without touching a single ledger row.
     */
    public long processedMinutesInPeriod(UUID userId, Instant periodStart) {
        return jdbcClient
                .sql("""
                        select coalesce(sum(minutes), 0)
                        from usage_ledger
                        where user_id = :userId
                          and period_start = :periodStart
                        """)
                .param("userId", userId)
                // The driver cannot infer a SQL type for Instant; OffsetDateTime
                // is what it maps onto timestamptz.
                .param("periodStart", periodStart.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .single();
    }

    public long countShows(UUID userId) {
        return jdbcClient
                .sql("select count(*) from shows where user_id = :userId")
                .param("userId", userId)
                .query(Long.class)
                .single();
    }
}
