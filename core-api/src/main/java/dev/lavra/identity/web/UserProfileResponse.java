package dev.lavra.identity.web;

import dev.lavra.identity.domain.Account;
import dev.lavra.identity.domain.UsageSnapshot;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code UserProfile} of the REST contract
 * ({@code contracts/openapi/core-api.v1.yaml}). The field names and nesting
 * follow the contract, not the domain — the contract is the source of truth.
 */
record UserProfileResponse(UUID id, String email, String displayName, PlanView plan, UsageView usage) {

    record PlanView(String code, String name) {
    }

    record UsageView(Instant periodStart,
                     Instant periodEnd,
                     long processedMinutesUsed,
                     int processedMinutesLimit,
                     long showsUsed,
                     int showsLimit) {
    }

    static UserProfileResponse from(Account account) {
        UsageSnapshot usage = account.usage();
        return new UserProfileResponse(
                account.id(),
                account.email(),
                account.displayName(),
                new PlanView(account.plan().code().name(), account.plan().name()),
                new UsageView(
                        account.period().start(),
                        account.period().end(),
                        usage.processedMinutesUsed(),
                        usage.processedMinutesLimit(),
                        usage.showsUsed(),
                        usage.showsLimit()));
    }
}
