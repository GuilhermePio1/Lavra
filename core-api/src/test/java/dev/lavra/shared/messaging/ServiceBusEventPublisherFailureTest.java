package dev.lavra.shared.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.core.exception.AzureException;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the port does when the broker is not there — the counterpart of
 * {@code AzureBlobStorageFailureTest}, and untagged on purpose: unlike the
 * emulator test, proving that a failure crosses the port as the port's own type
 * costs no container.
 */
class ServiceBusEventPublisherFailureTest {

    /** Emulator-shaped credentials aimed at a port nothing is listening on. */
    private static final String UNREACHABLE = "Endpoint=sb://127.0.0.1:10998;"
            + "SharedAccessKeyName=RootManageSharedAccessKey;"
            + "SharedAccessKey=SAS_KEY_VALUE;UseDevelopmentEmulator=true;";

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);

    private static ServiceBusEventPublisher publisher() {
        return new ServiceBusEventPublisher(
                new ServiceBusClientBuilder()
                        .connectionString(UNREACHABLE)
                        // The default policy would retry a connection nobody is
                        // going to accept, for a minute at a time.
                        .retryOptions(new AmqpRetryOptions()
                                .setMaxRetries(0)
                                .setTryTimeout(Duration.ofSeconds(5))),
                EventMapper.create(),
                CLOCK);
    }

    @Test
    @DisplayName("publishing to an unreachable broker fails as EventPublishException, not as an Azure type")
    void publishWrapsTheFailure() {
        UUID episodeId = UUID.randomUUID();

        assertThatThrownBy(() -> publisher().publish(EventType.EPISODE_UPLOADED_V1, new Payload(
                episodeId, UUID.randomUUID(), "raw/" + episodeId + "/original.mp3", "ep.mp3", "audio/mpeg")))
                .isInstanceOf(EventPublishException.class)
                .isNotInstanceOf(AzureException.class)
                // Naming the queue is the difference between an alert somebody
                // can act on and one that only says "Service Bus".
                .hasMessageContaining(EventType.EPISODE_UPLOADED_V1.queue())
                .hasMessageContaining("episode.uploaded.v1")
                .cause().isNotNull();
    }

    /** Mirrors the payload the emulator test publishes. */
    private record Payload(UUID episodeId, UUID showId, String rawAudioBlobPath,
                           String originalFilename, String contentType) {
    }
}
