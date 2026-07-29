package dev.lavra.identity;

import dev.lavra.identity.domain.Account;
import dev.lavra.identity.domain.BillingPeriod;
import dev.lavra.identity.domain.Plan;
import dev.lavra.identity.domain.UsageSnapshot;
import dev.lavra.identity.persistence.PlanRepository;
import dev.lavra.identity.persistence.SubscriptionEntity;
import dev.lavra.identity.persistence.SubscriptionRepository;
import dev.lavra.identity.persistence.UsageQueries;
import dev.lavra.identity.persistence.UserEntity;
import dev.lavra.identity.persistence.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Resolves the authenticated token into the local account, provisioning it on
 * first sight. Entry point of the identity feature: everything that needs to
 * know who is calling — and what they are entitled to — comes through here.
 */
@Service
public class IdentityService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final UsageQueries usageQueries;
    private final UserProvisioner userProvisioner;
    private final Clock clock;

    IdentityService(UserRepository userRepository,
                    SubscriptionRepository subscriptionRepository,
                    PlanRepository planRepository,
                    UsageQueries usageQueries,
                    UserProvisioner userProvisioner,
                    Clock clock) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.usageQueries = usageQueries;
        this.userProvisioner = userProvisioner;
        this.clock = clock;
    }

    /**
     * The account behind {@code jwt}, with plan and consumption for the period
     * that is current right now.
     *
     * <p>Not transactional as a whole: the JIT insert has to be able to fail
     * and be recovered from, which requires its transaction to end before this
     * method reacts to the failure.
     */
    public Account currentAccount(Jwt jwt) {
        TokenIdentity identity = TokenIdentity.from(jwt);
        UserEntity user = findOrProvision(identity);
        SubscriptionEntity subscription = currentSubscription(user);
        Plan plan = planFor(subscription);
        BillingPeriod period = subscription.period();

        return new Account(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                new UsageSnapshot(
                        plan,
                        period,
                        usageQueries.processedMinutesInPeriod(user.getId(), period.start()),
                        usageQueries.countShows(user.getId())));
    }

    private UserEntity findOrProvision(TokenIdentity identity) {
        return userRepository.findByExternalId(identity.externalId())
                .map(existing -> refreshFromToken(existing, identity))
                .orElseGet(() -> provision(identity));
    }

    private UserEntity provision(TokenIdentity identity) {
        try {
            return userProvisioner.provision(identity.externalId(), identity.email(), identity.displayName());
        } catch (DataIntegrityViolationException race) {
            // Two first requests arrived at once and the other one won. The
            // unique constraint on external_id is what makes that safe.
            return userRepository.findByExternalId(identity.externalId())
                    .orElseThrow(() -> new IllegalStateException(
                            "User provisioning failed for " + identity.externalId(), race));
        }
    }

    /** The token is the source of truth for name and e-mail; the local copy follows it. */
    private UserEntity refreshFromToken(UserEntity user, TokenIdentity identity) {
        if (user.refreshProfile(identity.email(), identity.displayName())) {
            return userRepository.save(user);
        }
        return user;
    }

    private SubscriptionEntity currentSubscription(UserEntity user) {
        SubscriptionEntity subscription = subscriptionRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "User " + user.getId() + " has no subscription"));

        Instant now = clock.instant();
        if (subscription.rollPeriodTo(now)) {
            // The period lapsed while the user was away: consumption resets by
            // moving the window, never by deleting ledger entries.
            return subscriptionRepository.save(subscription);
        }
        return subscription;
    }

    private Plan planFor(SubscriptionEntity subscription) {
        return planRepository.findById(subscription.getPlanCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown plan " + subscription.getPlanCode()))
                .toDomain();
    }
}
