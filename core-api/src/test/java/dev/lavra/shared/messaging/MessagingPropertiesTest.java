package dev.lavra.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The messaging half of {@code BlobStoragePropertiesTest}. */
class MessagingPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OnlyTheProperties.class);

    @EnableConfigurationProperties(MessagingProperties.class)
    static class OnlyTheProperties {
    }

    @Test
    @DisplayName("the local default binds")
    void bindsAValidConfiguration() {
        runner.withPropertyValues("lavra.messaging.connection-string=Endpoint=sb://localhost;"
                        + "SharedAccessKeyName=Root;SharedAccessKey=SAS_KEY_VALUE;UseDevelopmentEmulator=true;")
                .run(context -> assertThat(context).hasNotFailed()
                        .getBean(MessagingProperties.class)
                        .satisfies(properties ->
                                assertThat(properties.connectionString()).contains("UseDevelopmentEmulator=true")));
    }

    @Test
    @DisplayName("an empty connection string stops the context")
    void refusesABlankConnectionString() {
        runner.withPropertyValues("lavra.messaging.connection-string=")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("connectionString"));
    }
}
