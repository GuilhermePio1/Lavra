package dev.lavra.episode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.lavra.shared.blob.RecordingBlobStorage;
import dev.lavra.shared.messaging.EventType;
import dev.lavra.shared.messaging.RecordingEventPublisher;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The upload flow end to end, against a real Postgres with the real migrations.
 *
 * <p>Two collaborators are doubles, and for the same reason: neither storage nor
 * the broker is what these tests are about. The blob double also stands in for
 * the browser — the one participant in this flow that no test can call, since
 * the audio never passes through the API (ADR-0011). Azurite and the real broker
 * have their own tests.
 *
 * <p>Every event published here is validated against
 * {@code contracts/events/episode.uploaded.v1.json} on the way out, by the
 * recording publisher.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        // Never contacted: every test supplies an already-decoded token.
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8081/lavra"
})
class EpisodeIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @TestConfiguration(proxyBeanMethods = false)
    static class Doubles {

        @Bean
        @Primary
        RecordingBlobStorage recordingBlobStorage(Clock clock) {
            return new RecordingBlobStorage(clock);
        }

        @Bean
        @Primary
        RecordingEventPublisher recordingEventPublisher(Clock clock) {
            return new RecordingEventPublisher(clock);
        }
    }

    private static final long MP3_SIZE = 42_000_000L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbcClient;

    @Autowired
    RecordingBlobStorage blobStorage;

    @Autowired
    RecordingEventPublisher eventPublisher;

    @BeforeEach
    void resetDoubles() {
        blobStorage.clear();
        eventPublisher.clear();
    }

    /** A fresh identity per test, so no test inherits another's episodes or quota. */
    private static RequestPostProcessor someone() {
        String oid = "episode-user-" + UUID.randomUUID();
        return jwt().jwt(builder -> builder
                .claim("oid", oid)
                .claim("preferred_username", oid + "@lavra.dev")
                .claim("name", "Episode User"));
    }

    private String createShow(RequestPostProcessor token) throws Exception {
        String body = mockMvc.perform(post("/api/v1/shows").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Expulsando Demônios"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** The upload ticket as JSON, so a test can read whichever half it needs. */
    private String requestUpload(RequestPostProcessor token, String showId,
                                 String filename, String contentType, long sizeBytes) throws Exception {
        return mockMvc.perform(post("/api/v1/episodes").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": "%s", "filename": "%s", "contentType": "%s",
                                 "sizeBytes": %d, "title": "Husserl e o demônio"}
                                """.formatted(showId, filename, contentType, sizeBytes)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** An episode whose audio has already landed, exactly as declared. */
    private String uploadedEpisode(RequestPostProcessor token, String showId) throws Exception {
        String ticket = requestUpload(token, showId, "episodio.mp3", "audio/mpeg", MP3_SIZE);
        blobStorage.store(JsonPath.read(ticket, "$.upload.blobPath"), MP3_SIZE, "audio/mpeg");
        return JsonPath.read(ticket, "$.episode.id");
    }

    private void exhaustQuota(String showId, String episodeId) {
        // 90 minutes is the whole FREE allowance (V1 seeds the catalogue).
        jdbcClient.sql("""
                        insert into usage_ledger (user_id, episode_id, minutes, period_start)
                        select s.user_id, :episodeId, 90, sub.period_start
                        from shows s
                        join subscriptions sub on sub.user_id = s.user_id
                        where s.id = :showId
                        """)
                .param("episodeId", UUID.fromString(episodeId))
                .param("showId", UUID.fromString(showId))
                .update();
    }

    @Test
    @DisplayName("no token: 401 with the ApiError body of the contract")
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/episodes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("registering an episode returns it in PENDING_UPLOAD with a ticket for its own blob")
    void createsEpisodeAndIssuesWriteTicket() throws Exception {
        RequestPostProcessor token = someone();
        String showId = createShow(token);

        String body = mockMvc.perform(post("/api/v1/episodes").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": "%s", "filename": "episodio 01.mp3",
                                 "contentType": "audio/mpeg", "sizeBytes": %d,
                                 "title": "Husserl e o demônio"}
                                """.formatted(showId, MP3_SIZE)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.episode.status").value("PENDING_UPLOAD"))
                .andExpect(jsonPath("$.episode.workingTitle").value("Husserl e o demônio"))
                .andExpect(jsonPath("$.upload.url").isNotEmpty())
                .andExpect(jsonPath("$.upload.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String episodeId = JsonPath.read(body, "$.episode.id");
        // The path is derived from the episode, never from the name the client
        // sent — that is what makes the credential impossible to aim elsewhere.
        assertThat(JsonPath.<String>read(body, "$.upload.blobPath"))
                .isEqualTo("raw/" + episodeId + "/original.mp3");
        assertThat(blobStorage.issued())
                .singleElement()
                .satisfies(issued -> assertThat(issued.ttl()).isEqualTo(Duration.ofHours(2)));

        // Nothing is handed to the worker until the bytes are confirmed.
        assertThat(eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("registering against another user's show is a 404, like any unknown show")
    void refusesToRegisterAgainstAnotherUsersShow() throws Exception {
        String showOfA = createShow(someone());

        mockMvc.perform(post("/api/v1/episodes").with(someone())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": "%s", "filename": "episodio.mp3",
                                 "contentType": "audio/mpeg", "sizeBytes": 1024}
                                """.formatted(showOfA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("a content type outside the accepted formats is refused")
    void refusesUnsupportedContentType() throws Exception {
        RequestPostProcessor token = someone();
        String showId = createShow(token);

        mockMvc.perform(post("/api/v1/episodes").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": "%s", "filename": "episodio.mp4",
                                 "contentType": "video/mp4", "sizeBytes": 1024}
                                """.formatted(showId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("a filename that contradicts the declared type is refused")
    void refusesFilenameThatContradictsTheContentType() throws Exception {
        RequestPostProcessor token = someone();
        String showId = createShow(token);

        mockMvc.perform(post("/api/v1/episodes").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": "%s", "filename": "episodio.flac",
                                 "contentType": "audio/mpeg", "sizeBytes": 1024}
                                """.formatted(showId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("a file over 2 GB is refused with 413, before any episode exists")
    void refusesUploadOverTheSizeLimit() throws Exception {
        RequestPostProcessor token = someone();
        String showId = createShow(token);

        mockMvc.perform(post("/api/v1/episodes").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": "%s", "filename": "episodio.wav",
                                 "contentType": "audio/wav", "sizeBytes": 2147483649}
                                """.formatted(showId)))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("UPLOAD_TOO_LARGE"));

        mockMvc.perform(get("/api/v1/episodes").with(token))
                .andExpect(jsonPath("$.totalItems").value(0));
        assertThat(blobStorage.issued()).isEmpty();
    }

    @Test
    @DisplayName("an exhausted quota refuses the upload and says how much was used")
    void refusesUploadWhenTheQuotaIsExhausted() throws Exception {
        RequestPostProcessor token = someone();
        String showId = createShow(token);
        String first = uploadedEpisode(token, showId);
        exhaustQuota(showId, first);

        mockMvc.perform(post("/api/v1/episodes").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": "%s", "filename": "episodio.mp3",
                                 "contentType": "audio/mpeg", "sizeBytes": 1024}
                                """.formatted(showId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.message").value(containsString("90")));
    }

    @Test
    @DisplayName("confirming the upload starts the pipeline and hands the episode to the worker")
    void completesUploadAndPublishesTheEvent() throws Exception {
        RequestPostProcessor token = someone();
        String showId = createShow(token);
        String ticket = requestUpload(token, showId, "episodio.mp3", "audio/mpeg", MP3_SIZE);
        String episodeId = JsonPath.read(ticket, "$.episode.id");
        String blobPath = JsonPath.read(ticket, "$.upload.blobPath");
        blobStorage.store(blobPath, MP3_SIZE, "audio/mpeg");

        mockMvc.perform(post("/api/v1/episodes/" + episodeId + "/upload-complete").with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(episodeId))
                .andExpect(jsonPath("$.status").value("AUDIO_PROCESSING"));

        List<RecordingEventPublisher.Published> published =
                eventPublisher.publishedOf(EventType.EPISODE_UPLOADED_V1);
        assertThat(published).hasSize(1);

        // The schema was already checked on the way out; what matters here is
        // that the message describes this episode and not an empty shell.
        String message = published.getFirst().json();
        assertThat(JsonPath.<String>read(message, "$.eventType")).isEqualTo("episode.uploaded.v1");
        assertThat(JsonPath.<String>read(message, "$.data.episodeId")).isEqualTo(episodeId);
        assertThat(JsonPath.<String>read(message, "$.data.showId")).isEqualTo(showId);
        assertThat(JsonPath.<String>read(message, "$.data.rawAudioBlobPath")).isEqualTo(blobPath);
        assertThat(JsonPath.<String>read(message, "$.data.originalFilename")).isEqualTo("episodio.mp3");
        assertThat(JsonPath.<String>read(message, "$.data.contentType")).isEqualTo("audio/mpeg");

        // Both moves are in the history, in order, with both ends recorded.
        mockMvc.perform(get("/api/v1/episodes/" + episodeId).with(token))
                .andExpect(jsonPath("$.transitions.length()").value(2))
                .andExpect(jsonPath("$.transitions[0].fromStatus").value("PENDING_UPLOAD"))
                .andExpect(jsonPath("$.transitions[0].toStatus").value("RECEIVED"))
                .andExpect(jsonPath("$.transitions[1].toStatus").value("AUDIO_PROCESSING"))
                .andExpect(jsonPath("$.transitions[1].at").isNotEmpty())
                .andExpect(jsonPath("$.failure").doesNotExist());
    }

    @Test
    @DisplayName("confirming twice is harmless: the worker is not handed the episode again")
    void completeUploadIsIdempotent() throws Exception {
        RequestPostProcessor token = someone();
        String showId = createShow(token);
        String episodeId = uploadedEpisode(token, showId);

        mockMvc.perform(post("/api/v1/episodes/" + episodeId + "/upload-complete").with(token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/episodes/" + episodeId + "/upload-complete").with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUDIO_PROCESSING"));

        assertThat(eventPublisher.publishedOf(EventType.EPISODE_UPLOADED_V1)).hasSize(1);
        mockMvc.perform(get("/api/v1/episodes/" + episodeId).with(token))
                .andExpect(jsonPath("$.transitions.length()").value(2));
    }

    @Test
    @DisplayName("confirming an upload that never landed is a 400, and nothing starts")
    void refusesToCompleteWithoutTheBlob() throws Exception {
        RequestPostProcessor token = someone();
        String showId = createShow(token);
        String ticket = requestUpload(token, showId, "episodio.mp3", "audio/mpeg", MP3_SIZE);
        String episodeId = JsonPath.read(ticket, "$.episode.id");

        mockMvc.perform(post("/api/v1/episodes/" + episodeId + "/upload-complete").with(token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_FOUND"));

        assertThat(eventPublisher.published()).isEmpty();
        // Still awaiting its audio: the client may finish the upload and retry.
        mockMvc.perform(get("/api/v1/episodes/" + episodeId).with(token))
                .andExpect(jsonPath("$.status").value("PENDING_UPLOAD"))
                .andExpect(jsonPath("$.transitions.length()").value(0));
    }

    @Test
    @DisplayName("a blob that is not what was declared is a 400, whichever half differs")
    void refusesToCompleteWhenTheBlobDoesNotMatchTheDeclaration() throws Exception {
        RequestPostProcessor token = someone();
        String showId = createShow(token);
        String ticket = requestUpload(token, showId, "episodio.mp3", "audio/mpeg", MP3_SIZE);
        String episodeId = JsonPath.read(ticket, "$.episode.id");
        String blobPath = JsonPath.read(ticket, "$.upload.blobPath");

        // Truncated: right type, wrong size.
        blobStorage.store(blobPath, MP3_SIZE / 2, "audio/mpeg");
        mockMvc.perform(post("/api/v1/episodes/" + episodeId + "/upload-complete").with(token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_MISMATCH"));

        // Something else entirely: right size, wrong type.
        blobStorage.store(blobPath, MP3_SIZE, "application/octet-stream");
        mockMvc.perform(post("/api/v1/episodes/" + episodeId + "/upload-complete").with(token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_MISMATCH"));

        assertThat(eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("a long upload renews its ticket for the same blob")
    void renewsTheWriteTicketWhileAwaitingUpload() throws Exception {
        RequestPostProcessor token = someone();
        String showId = createShow(token);
        String ticket = requestUpload(token, showId, "episodio.wav", "audio/wav", MP3_SIZE);
        String episodeId = JsonPath.read(ticket, "$.episode.id");
        String blobPath = JsonPath.read(ticket, "$.upload.blobPath");

        mockMvc.perform(post("/api/v1/episodes/" + episodeId + "/upload-ticket").with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episode.id").value(episodeId))
                .andExpect(jsonPath("$.episode.status").value("PENDING_UPLOAD"))
                // The same blob: the blocks already sent are not thrown away.
                .andExpect(jsonPath("$.upload.blobPath").value(blobPath))
                .andExpect(jsonPath("$.upload.url").isNotEmpty());

        assertThat(blobStorage.issued()).hasSize(2);
    }

    @Test
    @DisplayName("renewing a ticket for an episode already uploading nothing is a 409")
    void refusesToRenewTheTicketAfterTheUploadIsDone() throws Exception {
        RequestPostProcessor token = someone();
        String showId = createShow(token);
        String episodeId = uploadedEpisode(token, showId);
        mockMvc.perform(post("/api/v1/episodes/" + episodeId + "/upload-complete").with(token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/episodes/" + episodeId + "/upload-ticket").with(token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EPISODE_NOT_AWAITING_UPLOAD"));
    }

    @Test
    @DisplayName("a failed episode refuses to have its upload confirmed")
    void refusesToCompleteAFailedEpisode() throws Exception {
        RequestPostProcessor token = someone();
        String episodeId = failedEpisode(token);

        mockMvc.perform(post("/api/v1/episodes/" + episodeId + "/upload-complete").with(token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EPISODE_FAILED"));
    }

    @Test
    @DisplayName("the failure block appears only on a failed episode, and says it can be retried")
    void reportsTheFailureOnTheDetail() throws Exception {
        RequestPostProcessor token = someone();
        String episodeId = failedEpisode(token);

        mockMvc.perform(get("/api/v1/episodes/" + episodeId).with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failure.stage").value("AUDIO_PROCESSING"))
                .andExpect(jsonPath("$.failure.errorCode").value("FFMPEG_FAILED"))
                .andExpect(jsonPath("$.failure.errorMessage").value("Corrupt audio stream"))
                .andExpect(jsonPath("$.failure.retryable").value(true));
    }

    @Test
    @DisplayName("another user's episode is indistinguishable from one that does not exist")
    void hidesEpisodesOfOtherUsers() throws Exception {
        RequestPostProcessor a = someone();
        String episodeOfA = uploadedEpisode(a, createShow(a));
        RequestPostProcessor b = someone();

        mockMvc.perform(get("/api/v1/episodes/" + episodeOfA).with(b))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(post("/api/v1/episodes/" + episodeOfA + "/upload-ticket").with(b))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/episodes/" + episodeOfA + "/upload-complete").with(b))
                .andExpect(status().isNotFound());

        // The same 404 an unknown id gets — that is the point.
        mockMvc.perform(get("/api/v1/episodes/" + UUID.randomUUID()).with(b))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/episodes").with(b))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    @DisplayName("the list is filtered by show and by status, and never crosses users")
    void filtersTheList() throws Exception {
        RequestPostProcessor token = someone();
        String showId = createShow(token);
        String uploaded = uploadedEpisode(token, showId);
        mockMvc.perform(post("/api/v1/episodes/" + uploaded + "/upload-complete").with(token))
                .andExpect(status().isOk());
        requestUpload(token, showId, "pendente.mp3", "audio/mpeg", MP3_SIZE);

        mockMvc.perform(get("/api/v1/episodes").with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.page").value(0));

        mockMvc.perform(get("/api/v1/episodes").with(token).param("showId", showId))
                .andExpect(jsonPath("$.totalItems").value(2));

        mockMvc.perform(get("/api/v1/episodes").with(token).param("status", "AUDIO_PROCESSING"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(uploaded));

        mockMvc.perform(get("/api/v1/episodes").with(token).param("status", "READY"))
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));

        // Filtering by someone else's show is a 404, not an empty list: the
        // filter must not become a way to probe for other people's shows.
        String showOfSomeoneElse = createShow(someone());
        mockMvc.perform(get("/api/v1/episodes").with(token).param("showId", showOfSomeoneElse))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the list is paged, most recent first")
    void pagesTheList() throws Exception {
        RequestPostProcessor token = someone();
        String showId = createShow(token);
        requestUpload(token, showId, "primeiro.mp3", "audio/mpeg", MP3_SIZE);
        requestUpload(token, showId, "segundo.mp3", "audio/mpeg", MP3_SIZE);
        String third = JsonPath.read(
                requestUpload(token, showId, "terceiro.mp3", "audio/mpeg", MP3_SIZE), "$.episode.id");

        mockMvc.perform(get("/api/v1/episodes").with(token).param("size", "1"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(third));

        mockMvc.perform(get("/api/v1/episodes").with(token).param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("pagination and filter values outside the contract are a 400, not a 500")
    void rejectsParametersOutsideTheContract() throws Exception {
        RequestPostProcessor token = someone();

        mockMvc.perform(get("/api/v1/episodes").with(token).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/episodes").with(token).param("size", "500"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/episodes").with(token).param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/episodes/not-a-uuid").with(token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /**
     * An episode that went through the whole upload flow and then blew up in the
     * worker — reached the way the pipeline would reach it, not by dropping an
     * episode straight into a state the machine would never allow.
     */
    private String failedEpisode(RequestPostProcessor token) throws Exception {
        String episodeId = uploadedEpisode(token, createShow(token));
        mockMvc.perform(post("/api/v1/episodes/" + episodeId + "/upload-complete").with(token))
                .andExpect(status().isOk());
        failEpisode(episodeId);
        return episodeId;
    }

    /** What consuming {@code processing.failed.v1} will do once that slice exists. */
    private void failEpisode(String episodeId) {
        jdbcClient.sql("""
                        update episodes
                        set status = 'FAILED',
                            failed_stage = 'AUDIO_PROCESSING',
                            error_code = 'FFMPEG_FAILED',
                            error_message = 'Corrupt audio stream'
                        where id = :id
                        """)
                .param("id", UUID.fromString(episodeId))
                .update();
    }
}
