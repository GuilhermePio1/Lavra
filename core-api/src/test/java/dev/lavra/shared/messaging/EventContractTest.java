package dev.lavra.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * What goes on the wire, checked against {@code contracts/events/} — the
 * envelope, the serialisation choices that the schemas depend on, and the fact
 * that the check actually bites.
 *
 * <p>No broker here: this is about the message, not the delivery.
 */
class EventContractTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:34:56Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final ObjectMapper mapper = EventMapper.create();
    private final RecordingEventPublisher publisher = new RecordingEventPublisher(CLOCK);

    /** The payload of episode.uploaded.v1, shaped exactly as the schema says. */
    record EpisodeUploaded(UUID episodeId, UUID showId, String rawAudioBlobPath,
                           String originalFilename, String contentType,
                           ProcessingOptions processingOptions) {
    }

    record ProcessingOptions(Boolean noiseReduction, Boolean silenceTrimming, Boolean stereo) {
    }

    private static EpisodeUploaded episodeUploaded(ProcessingOptions options) {
        UUID episodeId = UUID.randomUUID();
        return new EpisodeUploaded(
                episodeId,
                UUID.randomUUID(),
                "raw/" + episodeId + "/original.mp3",
                "ep-042-husserl.mp3",
                "audio/mpeg",
                options);
    }

    @Test
    @DisplayName("the envelope carries id, type and time, and the payload underneath")
    void buildsTheEnvelopeOfTheContract() {
        UUID eventId = publisher.publish(EventType.EPISODE_UPLOADED_V1, episodeUploaded(null));

        RecordingEventPublisher.Published message = publisher.published().getFirst();
        assertThat(message.eventId()).isEqualTo(eventId);

        JsonNode json = mapper.readTree(message.json());
        assertThat(json.get("eventId").asString()).isEqualTo(eventId.toString());
        assertThat(json.get("eventType").asString()).isEqualTo("episode.uploaded.v1");
        // ISO-8601, not an epoch number: the schema says date-time.
        assertThat(json.get("occurredAt").asString()).isEqualTo("2026-08-04T12:34:56Z");
        assertThat(json.get("data").get("contentType").asString()).isEqualTo("audio/mpeg");
    }

    @Test
    @DisplayName("an absent option is an absent key, never a null")
    void omitsOptionalKeysRatherThanSendingNull() {
        publisher.publish(EventType.EPISODE_UPLOADED_V1, episodeUploaded(null));

        JsonNode json = mapper.readTree(publisher.published().getFirst().json());
        assertThat(json.get("data").has("processingOptions")).isFalse();
    }

    @Test
    @DisplayName("a partially filled options object sends only the keys that were set")
    void sendsOnlyTheOptionsThatWereChosen() {
        publisher.publish(EventType.EPISODE_UPLOADED_V1,
                episodeUploaded(new ProcessingOptions(true, null, null)));

        JsonNode options = mapper.readTree(publisher.published().getFirst().json())
                .get("data").get("processingOptions");
        assertThat(options.get("noiseReduction").asBoolean()).isTrue();
        assertThat(options.has("silenceTrimming")).isFalse();
        assertThat(options.has("stereo")).isFalse();
    }

    @Test
    @DisplayName("a payload missing a required field never reaches the broker")
    void refusesAPayloadMissingARequiredField() {
        record Incomplete(UUID episodeId, String rawAudioBlobPath, String originalFilename, String contentType) {
        }
        UUID episodeId = UUID.randomUUID();

        assertThatThrownBy(() -> publisher.publish(EventType.EPISODE_UPLOADED_V1, new Incomplete(
                episodeId, "raw/" + episodeId + "/original.mp3", "ep.mp3", "audio/mpeg")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("showId");

        assertThat(publisher.published()).isEmpty();
    }

    @Test
    @DisplayName("a value outside the schema's enum never reaches the broker")
    void refusesAValueOutsideTheEnum() {
        assertThatThrownBy(() -> publisher.publish(EventType.EPISODE_UPLOADED_V1, new EpisodeUploaded(
                UUID.randomUUID(), UUID.randomUUID(), "raw/x/original.ogg", "ep.ogg", "audio/ogg", null)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("contentType");

        assertThat(publisher.published()).isEmpty();
    }

    @Test
    @DisplayName("a field the schema does not know never reaches the broker")
    void refusesAnUnknownField() {
        record WithExtra(UUID episodeId, UUID showId, String rawAudioBlobPath, String originalFilename,
                         String contentType, String internalNote) {
        }
        UUID episodeId = UUID.randomUUID();

        assertThatThrownBy(() -> publisher.publish(EventType.EPISODE_UPLOADED_V1, new WithExtra(
                episodeId, UUID.randomUUID(), "raw/" + episodeId + "/original.mp3",
                "ep.mp3", "audio/mpeg", "leaked internal detail")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("internalNote");

        assertThat(publisher.published()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(EventType.class)
    @DisplayName("every publishable event type has a schema in contracts/events/")
    void everyEventTypeIsUnderContract(EventType type) {
        assertThatCode(() -> EventSchemas.validate(type.wireName(), "{}"))
                .doesNotThrowAnyException();
        assertThat(EventSchemas.validate(type.wireName(), "{}"))
                .as("an empty object should violate the schema of %s", type.wireName())
                .isNotEmpty();
    }
}
