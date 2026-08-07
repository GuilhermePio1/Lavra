package dev.lavra.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The rule the guard enforces, exercised as the pure function it is — no Spring
 * context, so every combination is cheap to state.
 */
class DevelopmentDefaultsGuardTest {

    private static final String LOCAL_ISSUER = "http://localhost:8081/lavra";
    private static final String REAL_ISSUER = "https://lavra.ciamlogin.com/lavra.onmicrosoft.com/v2.0";

    private static final String AZURITE = "UseDevelopmentStorage=true";
    private static final String EMULATOR_BUS = "Endpoint=sb://localhost;SharedAccessKeyName=Root;"
            + "SharedAccessKey=SAS_KEY_VALUE;UseDevelopmentEmulator=true;";

    private static final String REAL_STORAGE = "DefaultEndpointsProtocol=https;AccountName=lavraprod;"
            + "AccountKey=Zm9vYmFy;EndpointSuffix=core.windows.net";
    private static final String REAL_BUS = "Endpoint=sb://lavra.servicebus.windows.net/;"
            + "SharedAccessKeyName=send;SharedAccessKey=Zm9vYmFy";

    @Nested
    @DisplayName("the local stack")
    class LocalStack {

        @Test
        @DisplayName("is exactly what the defaults describe, and starts")
        void localIssuerWithLocalInfrastructureIsFine() {
            assertThat(DevelopmentDefaultsGuard.rejectionReason(LOCAL_ISSUER, AZURITE, EMULATOR_BUS)).isEmpty();
        }

        @Test
        @DisplayName("is still local when the emulator moved to a mapped port, as Testcontainers does")
        void localIssuerOnAnyPortIsFine() {
            assertThat(DevelopmentDefaultsGuard.rejectionReason(
                    "http://127.0.0.1:49512/lavra", AZURITE, EMULATOR_BUS)).isEmpty();
        }
    }

    @Nested
    @DisplayName("a real environment")
    class RealEnvironment {

        @Test
        @DisplayName("starts when its infrastructure is real too")
        void realIssuerWithRealInfrastructureIsFine() {
            assertThat(DevelopmentDefaultsGuard.rejectionReason(REAL_ISSUER, REAL_STORAGE, REAL_BUS)).isEmpty();
        }

        @Test
        @DisplayName("is refused when the storage variable was forgotten — the failure this exists for")
        void realIssuerWithAzuriteIsRefused() {
            assertThat(DevelopmentDefaultsGuard.rejectionReason(REAL_ISSUER, AZURITE, REAL_BUS))
                    .hasValueSatisfying(reason -> assertThat(reason)
                            .contains("lavra.blob.connection-string")
                            .contains("LAVRA_BLOB_CONNECTION_STRING")
                            .doesNotContain("lavra.messaging.connection-string"));
        }

        @Test
        @DisplayName("is refused when Azurite is spelled out as the well-known account instead")
        void recognisesAzuriteWrittenInFull() {
            String spelledOut = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Zm9v;"
                    + "BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;";
            assertThat(DevelopmentDefaultsGuard.rejectionReason(REAL_ISSUER, spelledOut, REAL_BUS)).isPresent();
        }

        @Test
        @DisplayName("is refused when the broker variable was forgotten")
        void realIssuerWithEmulatorBusIsRefused() {
            assertThat(DevelopmentDefaultsGuard.rejectionReason(REAL_ISSUER, REAL_STORAGE, EMULATOR_BUS))
                    .hasValueSatisfying(reason -> assertThat(reason)
                            .contains("lavra.messaging.connection-string")
                            .doesNotContain("lavra.blob.connection-string"));
        }

        @Test
        @DisplayName("names both when both were forgotten, so one restart fixes everything")
        void reportsEveryOffenderAtOnce() {
            assertThat(DevelopmentDefaultsGuard.rejectionReason(REAL_ISSUER, AZURITE, EMULATOR_BUS))
                    .hasValueSatisfying(reason -> assertThat(reason)
                            .contains("lavra.blob.connection-string")
                            .contains("lavra.messaging.connection-string"));
        }
    }

    @Nested
    @DisplayName("an issuer that makes no sense")
    class BrokenIssuer {

        @Test
        @DisplayName("counts as not local: a blank issuer must not license development storage")
        void blankIssuerIsNotLocal() {
            assertThat(DevelopmentDefaultsGuard.rejectionReason("", AZURITE, REAL_BUS)).isPresent();
        }

        @Test
        @DisplayName("counts as not local when it cannot be parsed at all")
        void unparseableIssuerIsNotLocal() {
            assertThat(DevelopmentDefaultsGuard.rejectionReason(":: not a uri ::", AZURITE, REAL_BUS)).isPresent();
        }

        @Test
        @DisplayName("is not fooled by a hostname that merely starts with localhost")
        void lookalikeHostIsNotLocal() {
            assertThat(DevelopmentDefaultsGuard.rejectionReason(
                    "https://localhost.attacker.example/lavra", AZURITE, REAL_BUS)).isPresent();
        }
    }

    /**
     * The rule above is only worth having if it is actually wired to startup.
     * These two run the guard as the bean it is.
     */
    @Nested
    @DisplayName("wired into a context")
    class Wiring {

        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(DevelopmentDefaultsGuard.class);

        @Test
        @DisplayName("a refusal stops the context, it does not merely compute a reason")
        void refusalStopsStartup() {
            runner.withPropertyValues(
                            "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + REAL_ISSUER,
                            "lavra.blob.connection-string=" + AZURITE,
                            "lavra.messaging.connection-string=" + REAL_BUS)
                    .run(context -> assertThat(context).hasFailed()
                            .getFailure()
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("Refusing to start"));
        }

        @Test
        @DisplayName("the local stack starts, which is every test in this codebase")
        void localStackStarts() {
            runner.withPropertyValues(
                            "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + LOCAL_ISSUER,
                            "lavra.blob.connection-string=" + AZURITE,
                            "lavra.messaging.connection-string=" + EMULATOR_BUS)
                    .run(context -> assertThat(context).hasNotFailed());
        }
    }
}
