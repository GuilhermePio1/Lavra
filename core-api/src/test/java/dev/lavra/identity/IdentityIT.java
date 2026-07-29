package dev.lavra.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.lavra.identity.domain.PlanCode;
import dev.lavra.identity.persistence.SubscriptionEntity;
import dev.lavra.identity.persistence.SubscriptionRepository;
import dev.lavra.identity.persistence.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The identity slice end to end, against a real Postgres with the real Flyway
 * migration — which also proves the JPA mapping matches the schema, since
 * {@code ddl-auto: validate} would fail the context otherwise.
 *
 * <p>Authentication is exercised through the production security configuration:
 * no mocked {@code JwtDecoder}, no relaxed profile. The test only supplies an
 * already-authenticated token, which is what the IdP would have done.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        // Never contacted: the tests either send no token at all, or supply an
        // already-decoded one. Present so the resource server can configure.
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8081/lavra"
})
class IdentityIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    private static RequestPostProcessor tokenFor(String oid, String email, String name) {
        return jwt().jwt(builder -> builder
                .claim("oid", oid)
                .claim("preferred_username", email)
                .claim("name", name));
    }

    @Test
    @DisplayName("no token: 401 with the ApiError body of the contract")
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("a malformed token also answers the ApiError body, not an empty 401")
    void rejectsMalformedTokenWithApiError() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("health probes stay open — the only route without authentication")
    void allowsHealthWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("first login provisions the local user and a FREE subscription")
    void firstRequestProvisionsUserAndFreePlan() throws Exception {
        String oid = "new-user-" + System.nanoTime();

        mockMvc.perform(get("/api/v1/me").with(tokenFor(oid, oid + "@lavra.dev", "New User")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value(oid + "@lavra.dev"))
                .andExpect(jsonPath("$.displayName").value("New User"))
                .andExpect(jsonPath("$.plan.code").value("FREE"))
                .andExpect(jsonPath("$.plan.name").value("Free"))
                // Entitlements come from the catalogue seeded by the migration.
                .andExpect(jsonPath("$.usage.processedMinutesLimit").value(90))
                .andExpect(jsonPath("$.usage.showsLimit").value(1))
                .andExpect(jsonPath("$.usage.processedMinutesUsed").value(0))
                .andExpect(jsonPath("$.usage.showsUsed").value(0))
                .andExpect(jsonPath("$.usage.periodStart").isNotEmpty())
                .andExpect(jsonPath("$.usage.periodEnd").isNotEmpty());

        var user = userRepository.findByExternalId(oid);
        assertThat(user).isPresent();

        Optional<SubscriptionEntity> subscription = subscriptionRepository.findByUserId(user.orElseThrow().getId());
        assertThat(subscription).isPresent();
        assertThat(subscription.orElseThrow().getPlanCode()).isEqualTo(PlanCode.FREE);
    }

    @Test
    @DisplayName("a returning user is not provisioned twice")
    void secondRequestReusesTheSameUser() throws Exception {
        String oid = "returning-user-" + System.nanoTime();
        RequestPostProcessor token = tokenFor(oid, oid + "@lavra.dev", "Returning User");

        String firstId = mockMvc.perform(get("/api/v1/me").with(token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String secondId = mockMvc.perform(get("/api/v1/me").with(token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(firstId).isEqualTo(secondId);
        assertThat(userRepository.findByExternalId(oid)).isPresent();
        assertThat(userRepository.findAll().stream()
                .filter(candidate -> candidate.getExternalId().equals(oid))
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("two tokens are two accounts — identity is keyed by the oid claim")
    void differentTokensGetDifferentAccounts() throws Exception {
        String suffix = String.valueOf(System.nanoTime());

        String first = mockMvc.perform(get("/api/v1/me")
                        .with(tokenFor("user-a-" + suffix, "a-" + suffix + "@lavra.dev", "A")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(get("/api/v1/me")
                        .with(tokenFor("user-b-" + suffix, "b-" + suffix + "@lavra.dev", "B")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("the local record follows the token when the IdP profile changes")
    void refreshesProfileFromTheToken() throws Exception {
        String oid = "renamed-user-" + System.nanoTime();

        mockMvc.perform(get("/api/v1/me").with(tokenFor(oid, oid + "@lavra.dev", "Old Name")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me").with(tokenFor(oid, oid + "@new.dev", "New Name")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(oid + "@new.dev"))
                .andExpect(jsonPath("$.displayName").value("New Name"));
    }
}
