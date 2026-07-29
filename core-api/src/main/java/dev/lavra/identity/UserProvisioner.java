package dev.lavra.identity;

import dev.lavra.identity.domain.BillingPeriod;
import dev.lavra.identity.domain.PlanCode;
import dev.lavra.identity.persistence.SubscriptionEntity;
import dev.lavra.identity.persistence.SubscriptionRepository;
import dev.lavra.identity.persistence.UserEntity;
import dev.lavra.identity.persistence.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Just-in-time provisioning: the local user record is created on the first
 * authenticated request, since Entra owns identity and never tells us about a
 * sign-up (spec 0004).
 *
 * <p>Separate bean on purpose — the caller has to be able to catch the unique
 * violation of a concurrent first request <em>after</em> this transaction has
 * rolled back, which self-invocation would not allow.
 */
@Component
class UserProvisioner {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;

    UserProvisioner(UserRepository userRepository, SubscriptionRepository subscriptionRepository, Clock clock) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    /**
     * Creates the user and their FREE subscription atomically — a user without
     * a plan would have no entitlements and could never be served.
     */
    @Transactional
    UserEntity provision(String externalId, String email, String displayName) {
        Instant now = clock.instant();
        UserEntity user = userRepository.saveAndFlush(new UserEntity(externalId, email, displayName, now));
        subscriptionRepository.save(new SubscriptionEntity(
                user.getId(),
                PlanCode.defaultForNewUser(),
                BillingPeriod.monthlyStartingAt(now),
                now));
        return user;
    }
}
