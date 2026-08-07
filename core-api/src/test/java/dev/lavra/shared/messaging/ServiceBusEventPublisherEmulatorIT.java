package dev.lavra.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.azure.ServiceBusEmulatorContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The publisher against a real Azure Service Bus emulator: proves the message
 * actually lands on the queue the worker will listen to, with the envelope and
 * the broker properties the pipeline depends on.
 *
 * <p>Tagged {@code emulator} and excluded from {@code gradle test} — it drags a
 * SQL Server container along. Run it with {@code gradle emulatorTest}, and after
 * any change to the publisher or to the queue names.
 *
 * <p>The queues come from {@code infra/servicebus-config.json}, the same file
 * the local stack uses: a queue renamed there and not here fails this test
 * rather than production.
 */
@Tag("emulator")
@Testcontainers
class ServiceBusEventPublisherEmulatorIT {

    private static final Network NETWORK = Network.newNetwork();

    @Container
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
                    .acceptLicense()
                    .withNetwork(NETWORK);

    @Container
    static final ServiceBusEmulatorContainer SERVICE_BUS =
            new ServiceBusEmulatorContainer("mcr.microsoft.com/azure-messaging/servicebus-emulator:2.0.1")
                    .acceptLicense()
                    .withNetwork(NETWORK)
                    .withConfig(emulatorConfig())
                    .withMsSqlServerContainer(SQL_SERVER);

    private static final Instant NOW = Instant.parse("2026-08-04T12:34:56Z");

    private final ObjectMapper mapper = EventMapper.create();

    private static Transferable emulatorConfig() {
        try {
            return Transferable.of(Files.readString(Path.of("..", "infra", "servicebus-config.json")));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read infra/servicebus-config.json", e);
        }
    }

    private ServiceBusEventPublisher publisher() {
        return new ServiceBusEventPublisher(
                new ServiceBusClientBuilder().connectionString(SERVICE_BUS.getConnectionString()),
                mapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static List<ServiceBusReceivedMessage> drain(String queue, int howMany) {
        try (ServiceBusReceiverClient receiver = new ServiceBusClientBuilder()
                .connectionString(SERVICE_BUS.getConnectionString())
                .receiver()
                .queueName(queue)
                .buildClient()) {
            return receiver.receiveMessages(howMany, Duration.ofSeconds(20)).stream().toList();
        }
    }

    @Test
    @DisplayName("the event lands on its queue, under contract and identifiable")
    void publishesToTheQueueOfTheEventType() {
        UUID episodeId = UUID.randomUUID();
        UUID showId = UUID.randomUUID();

        UUID eventId = publisher().publish(EventType.EPISODE_UPLOADED_V1, new EpisodeUploadedPayload(
                episodeId, showId, "raw/" + episodeId + "/original.mp3", "ep-042.mp3", "audio/mpeg"));

        List<ServiceBusReceivedMessage> received = drain(EventType.EPISODE_UPLOADED_V1.queue(), 1);
        assertThat(received).hasSize(1);
        ServiceBusReceivedMessage message = received.getFirst();

        // Deduplication on redelivery depends on this being the eventId.
        assertThat(message.getMessageId()).isEqualTo(eventId.toString());
        assertThat(message.getSubject()).isEqualTo("episode.uploaded.v1");
        assertThat(message.getContentType()).isEqualTo("application/json");

        String body = message.getBody().toString();
        EventSchemas.assertValid("episode.uploaded.v1", body);

        JsonNode json = mapper.readTree(body);
        assertThat(json.get("eventId").asString()).isEqualTo(eventId.toString());
        assertThat(json.get("occurredAt").asString()).isEqualTo("2026-08-04T12:34:56Z");
        assertThat(json.get("data").get("episodeId").asString()).isEqualTo(episodeId.toString());
        assertThat(json.get("data").get("showId").asString()).isEqualTo(showId.toString());
    }

    @Test
    @DisplayName("a sender is reused across events, and each message keeps its own id")
    void reusesTheSenderAcrossPublications() {
        ServiceBusEventPublisher publisher = publisher();
        UUID first = publisher.publish(EventType.EPISODE_UPLOADED_V1, somePayload());
        UUID second = publisher.publish(EventType.EPISODE_UPLOADED_V1, somePayload());
        publisher.close();

        List<String> ids = drain(EventType.EPISODE_UPLOADED_V1.queue(), 2).stream()
                .map(ServiceBusReceivedMessage::getMessageId)
                .toList();

        assertThat(ids).containsExactlyInAnyOrder(first.toString(), second.toString());
    }

    private static EpisodeUploadedPayload somePayload() {
        UUID episodeId = UUID.randomUUID();
        return new EpisodeUploadedPayload(episodeId, UUID.randomUUID(),
                "raw/" + episodeId + "/original.mp3", "ep.mp3", "audio/mpeg");
    }

    /** The payload of episode.uploaded.v1; the episode slice will own the real one. */
    record EpisodeUploadedPayload(UUID episodeId, UUID showId, String rawAudioBlobPath,
                                  String originalFilename, String contentType) {
    }
}
