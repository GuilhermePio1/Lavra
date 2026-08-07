package dev.lavra.shared.blob;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * That the validation actually runs, and stops the context rather than being
 * decorative annotations. Only the properties are enabled — no clients, no
 * database — so this costs milliseconds.
 */
class BlobStoragePropertiesTest {

    private static final String VALID_CONNECTION = "lavra.blob.connection-string=UseDevelopmentStorage=true";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OnlyTheProperties.class);

    @EnableConfigurationProperties(BlobStorageProperties.class)
    static class OnlyTheProperties {
    }

    @Test
    @DisplayName("the local defaults bind")
    void bindsAValidConfiguration() {
        runner.withPropertyValues(VALID_CONNECTION, "lavra.blob.container=episodes",
                        "lavra.blob.auto-create-container=true")
                .run(context -> assertThat(context).hasNotFailed()
                        .getBean(BlobStorageProperties.class)
                        .satisfies(properties -> {
                            assertThat(properties.container()).isEqualTo("episodes");
                            assertThat(properties.autoCreateContainer()).isTrue();
                        }));
    }

    @Test
    @DisplayName("an empty connection string stops the context — the shape a broken secret takes")
    void refusesABlankConnectionString() {
        runner.withPropertyValues("lavra.blob.connection-string=", "lavra.blob.container=episodes")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        // The top-level message only says binding failed; which
                        // field and why is in the BindValidationException under
                        // it, which is also what an operator reads in the log.
                        .rootCause()
                        .hasMessageContaining("connectionString"));
    }

    @Test
    @DisplayName("a container name Azure would reject stops the context, not the first upload")
    void refusesAnInvalidContainerName() {
        // Capitals are the realistic typo: legal in a YAML file, illegal in Azure.
        runner.withPropertyValues(VALID_CONNECTION, "lavra.blob.container=Episodes")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("container")
                        .hasMessageContaining("valid Azure container name"));
    }

    @Test
    @DisplayName("a container name too short for Azure is refused")
    void refusesATooShortContainerName() {
        runner.withPropertyValues(VALID_CONNECTION, "lavra.blob.container=ep")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("a container name with a trailing hyphen is refused")
    void refusesATrailingHyphen() {
        runner.withPropertyValues(VALID_CONNECTION, "lavra.blob.container=episodes-")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("hyphens inside the name are fine, as Azure allows")
    void acceptsInnerHyphens() {
        runner.withPropertyValues(VALID_CONNECTION, "lavra.blob.container=lavra-episodes")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
