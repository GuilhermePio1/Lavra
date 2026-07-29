package dev.lavra.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * Drives the real OIDC emulator through the real interactive login flow, then
 * calls the API with the token it issued — signature, issuer and audience all
 * checked by the production {@code JwtDecoder}.
 *
 * <p>This exists because the emulator's configuration is the one part of
 * authentication that no {@code jwt()} post-processor can cover: those inject an
 * already-decoded token and never touch issuance. A {@code JSON_CONFIG} that
 * silently emits tokens without {@code aud} — or with an unresolved
 * {@code ${subject}} placeholder that collapses every developer into one
 * account — passes every other test in this suite while making the application
 * unusable locally. Both were real bugs; this test is what keeps them fixed.
 *
 * <p>The config below mirrors {@code infra/docker-compose.dev.yml}, minus the
 * {@code $$} escaping that only Compose needs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DevOidcEmulatorIT {

    private static final String ISSUER_ID = "lavra";
    private static final int OIDC_PORT = 8080;
    private static final String REDIRECT_URI = "http://localhost:3000/callback";

    private static final String JSON_CONFIG = """
            {
              "interactiveLogin": true,
              "tokenCallbacks": [
                {
                  "issuerId": "lavra",
                  "requestMappings": [
                    {
                      "requestParam": "subject",
                      "match": "*",
                      "claims": {
                        "aud": ["api://lavra-core"],
                        "tid": "11111111-1111-1111-1111-111111111111",
                        "oid": "${subject}",
                        "preferred_username": "${subject}@lavra.dev",
                        "name": "${subject}"
                      }
                    }
                  ]
                }
              ]
            }
            """;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static final GenericContainer<?> OIDC =
            new GenericContainer<>("ghcr.io/navikt/mock-oauth2-server:5.0.2")
                    .withExposedPorts(OIDC_PORT)
                    .withEnv("JSON_CONFIG", JSON_CONFIG)
                    .waitingFor(Wait.forHttp("/" + ISSUER_ID + "/.well-known/openid-configuration")
                            .forPort(OIDC_PORT)
                            .forStatusCode(200));

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void oidcProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", DevOidcEmulatorIT::issuer);
    }

    private static String issuer() {
        return issuer(ISSUER_ID);
    }

    private static String issuer(String issuerId) {
        return "http://" + OIDC.getHost() + ":" + OIDC.getMappedPort(OIDC_PORT) + "/" + issuerId;
    }

    /** Signs in as {@code username} the way the login screen does, and returns the access token. */
    private static String signIn(String username, String extraClaims) throws Exception {
        return signIn(username, extraClaims, ISSUER_ID);
    }

    private static String signIn(String username, String extraClaims, String issuerId) throws Exception {
        String authorizeUrl = issuer(issuerId) + "/authorize"
                + "?client_id=lavra-frontend&response_type=code&scope=openid&state=test"
                + "&redirect_uri=" + encode(REDIRECT_URI);

        HttpResponse<String> login = HTTP.send(
                HttpRequest.newBuilder(URI.create(authorizeUrl))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "username=" + encode(username) + "&claims=" + encode(extraClaims)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        String location = login.headers().firstValue("location")
                .orElseThrow(() -> new IllegalStateException("login did not redirect: " + login.statusCode()));
        String code = location.replaceFirst(".*[?&]code=([^&]*).*", "$1");

        HttpResponse<String> token = HTTP.send(
                HttpRequest.newBuilder(URI.create(issuer(issuerId) + "/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "grant_type=authorization_code"
                                        + "&code=" + encode(code)
                                        + "&redirect_uri=" + encode(REDIRECT_URI)
                                        + "&client_id=lavra-frontend&client_secret=dev"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        return JSON.readTree(token.body()).path("access_token").asString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a token from the interactive login is accepted and provisions that user")
    void tokenFromInteractiveLoginIsAccepted() throws Exception {
        String accessToken = signIn("guilherme", "{}");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("guilherme@lavra.dev"))
                .andExpect(jsonPath("$.displayName").value("guilherme"))
                .andExpect(jsonPath("$.plan.code").value("FREE"));
    }

    @Test
    @DisplayName("switching user on the login screen switches account — posse depends on it")
    void differentLoginsAreDifferentAccounts() throws Exception {
        String first = mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + signIn("ana", "{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@lavra.dev"))
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + signIn("bruno", "{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bruno@lavra.dev"))
                .andReturn().getResponse().getContentAsString();

        // Guards the ${subject} placeholder: unresolved, every developer would
        // share a single account and ownership rules would be untestable.
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("the login screen can grant ADMIN — so `roles` must stay out of the mapping")
    void loginScreenCanAddRoles() throws Exception {
        String accessToken = signIn("admin-user", "{\"roles\":[\"ADMIN\"]}");

        // Claims set by the requestMapping cannot be overridden from the login
        // screen; this asserts `roles` is still overridable.
        String claims = new String(java.util.Base64.getUrlDecoder()
                .decode(accessToken.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("ADMIN");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a token from another issuer is rejected")
    void rejectsTokenFromAnotherIssuer() throws Exception {
        // The emulator serves any issuer path, configured or not. This one has
        // no tokenCallback, so it mimics both failure modes at once: wrong
        // issuer and no audience.
        String foreignToken = signIn("intruder", "{}", "other-tenant");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
