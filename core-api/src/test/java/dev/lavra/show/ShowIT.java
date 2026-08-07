package dev.lavra.show;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The show slice end to end: ownership, the plan's show allowance and the voice
 * profile, against a real Postgres with the real migrations.
 *
 * <p>As in {@code IdentityIT}, authentication runs through the production
 * security configuration — the test only supplies an already-authenticated
 * token, which is what the IdP would have done.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        // Never contacted: every test here supplies an already-decoded token or
        // none at all, so the decoder is never materialised.
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8081/lavra"
})
class ShowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbcClient;

    /** A fresh identity per test, so no test inherits another's shows or quota. */
    private static RequestPostProcessor someone() {
        String oid = "show-user-" + UUID.randomUUID();
        return jwt().jwt(builder -> builder
                .claim("oid", oid)
                .claim("preferred_username", oid + "@lavra.dev")
                .claim("name", "Show User"));
    }

    private String createShow(RequestPostProcessor token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/shows").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /**
     * Inserts an episode straight into the table rather than going through the
     * upload flow: what is being tested is the show's reaction to episodes
     * existing at all, not how they got there. The declaration columns are
     * filled because the schema requires every episode to have one.
     */
    private void insertEpisode(String showId) {
        jdbcClient.sql("""
                        insert into episodes (id, show_id, status,
                                              original_filename, content_type, size_bytes)
                        values (:id, :showId, 'RECEIVED', 'episodio.mp3', 'audio/mpeg', 1024)
                        """)
                .param("id", UUID.randomUUID())
                .param("showId", UUID.fromString(showId))
                .update();
    }

    private void switchToStudioPlan(String showId) {
        jdbcClient.sql("""
                        update subscriptions set plan_code = 'STUDIO'
                        where user_id = (select user_id from shows where id = :showId)
                        """)
                .param("showId", UUID.fromString(showId))
                .update();
    }

    @Test
    @DisplayName("no token: 401 with the ApiError body of the contract")
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/shows"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("creating a show returns it with an empty episode count and a Location header")
    void createsShow() throws Exception {
        RequestPostProcessor token = someone();

        String body = mockMvc.perform(post("/api/v1/shows").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Expulsando Demônios", "description": "Filosofia com humor ácido."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Expulsando Demônios"))
                .andExpect(jsonPath("$.description").value("Filosofia com humor ácido."))
                .andExpect(jsonPath("$.episodeCount").value(0))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(body, "$.id");
        mockMvc.perform(get("/api/v1/shows").with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id));
    }

    @Test
    @DisplayName("a blank name is rejected before anything is created")
    void rejectsBlankName() throws Exception {
        RequestPostProcessor token = someone();

        mockMvc.perform(post("/api/v1/shows").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/shows").with(token))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("the FREE plan allows a single show; the second is refused with the plan code")
    void enforcesTheShowAllowanceOfThePlan() throws Exception {
        RequestPostProcessor token = someone();
        createShow(token, "Primeiro");

        mockMvc.perform(post("/api/v1/shows").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Segundo"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_SHOW_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("a plan change takes effect on the very next request")
    void honoursAPlanChangeImmediately() throws Exception {
        RequestPostProcessor token = someone();
        String first = createShow(token, "Primeiro");

        mockMvc.perform(post("/api/v1/shows").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Segundo"}
                                """))
                .andExpect(status().isForbidden());

        switchToStudioPlan(first);

        mockMvc.perform(post("/api/v1/shows").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Segundo"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/shows").with(token))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    @DisplayName("another user's show is indistinguishable from one that does not exist")
    void hidesShowsOfOtherUsers() throws Exception {
        String showOfA = createShow(someone(), "Show do A");
        RequestPostProcessor b = someone();

        mockMvc.perform(get("/api/v1/shows/" + showOfA).with(b))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(patch("/api/v1/shows/" + showOfA).with(b)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sequestrado"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/shows/" + showOfA).with(b))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/shows/" + showOfA + "/voice-profile").with(b))
                .andExpect(status().isNotFound());

        // The same 404 an unknown id gets — that is the point.
        mockMvc.perform(get("/api/v1/shows/" + UUID.randomUUID()).with(b))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/shows").with(b))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("a patch touches only the fields it carries")
    void patchesOnlyWhatIsSent() throws Exception {
        RequestPostProcessor token = someone();
        String id = createShow(token, "Nome original");

        mockMvc.perform(patch("/api/v1/shows/" + id).with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Agora com descrição."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome original"))
                .andExpect(jsonPath("$.description").value("Agora com descrição."));

        mockMvc.perform(patch("/api/v1/shows/" + id).with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Nome novo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome novo"))
                .andExpect(jsonPath("$.description").value("Agora com descrição."));
    }

    @Test
    @DisplayName("an empty show can be deleted and is gone afterwards")
    void deletesAnEmptyShow() throws Exception {
        RequestPostProcessor token = someone();
        String id = createShow(token, "Descartável");

        mockMvc.perform(delete("/api/v1/shows/" + id).with(token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/shows/" + id).with(token))
                .andExpect(status().isNotFound());

        // The freed slot is usable again — the allowance counts shows, not creations.
        mockMvc.perform(post("/api/v1/shows").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "No lugar"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a show with episodes refuses deletion instead of cascading")
    void refusesToDeleteShowWithEpisodes() throws Exception {
        RequestPostProcessor token = someone();
        String id = createShow(token, "Com episódios");
        insertEpisode(id);

        mockMvc.perform(delete("/api/v1/shows/" + id).with(token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOW_NOT_EMPTY"));

        mockMvc.perform(get("/api/v1/shows/" + id).with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episodeCount").value(1));
    }

    @Test
    @DisplayName("the episode count is reported on both the detail and the list")
    void reportsEpisodeCount() throws Exception {
        RequestPostProcessor token = someone();
        String id = createShow(token, "Contagem");
        insertEpisode(id);
        insertEpisode(id);

        mockMvc.perform(get("/api/v1/shows/" + id).with(token))
                .andExpect(jsonPath("$.episodeCount").value(2));

        mockMvc.perform(get("/api/v1/shows").with(token))
                .andExpect(jsonPath("$.items[0].episodeCount").value(2));
    }

    @Test
    @DisplayName("a new show has a blank voice profile rather than a missing one")
    void voiceProfileStartsBlank() throws Exception {
        RequestPostProcessor token = someone();
        String id = createShow(token, "Sem perfil");

        mockMvc.perform(get("/api/v1/shows/" + id + "/voice-profile").with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toneDescription").value(""))
                .andExpect(jsonPath("$.antiExamples.length()").value(0))
                .andExpect(jsonPath("$.examples.length()").value(0));
    }

    @Test
    @DisplayName("editing the voice profile replaces tone and anti-examples")
    void replacesTheEditablePartOfTheVoiceProfile() throws Exception {
        RequestPostProcessor token = someone();
        String id = createShow(token, "Com perfil");

        mockMvc.perform(put("/api/v1/shows/" + id + "/voice-profile").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toneDescription": "irreverente, filosófico, primeira pessoa",
                                 "antiExamples": ["Neste episódio, exploramos…", "clickbait com reticências"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toneDescription").value("irreverente, filosófico, primeira pessoa"))
                .andExpect(jsonPath("$.antiExamples.length()").value(2))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        // Persisted, not just echoed back.
        mockMvc.perform(get("/api/v1/shows/" + id + "/voice-profile").with(token))
                .andExpect(jsonPath("$.toneDescription").value("irreverente, filosófico, primeira pessoa"));

        // PUT replaces: the previous anti-examples are gone, not merged.
        mockMvc.perform(put("/api/v1/shows/" + id + "/voice-profile").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toneDescription": "sóbrio", "antiExamples": []}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toneDescription").value("sóbrio"))
                .andExpect(jsonPath("$.antiExamples.length()").value(0));
    }

    @Test
    @DisplayName("editing the voice profile cannot erase the examples earned by approval")
    void keepsApprovedExamplesAcrossAnEdit() throws Exception {
        RequestPostProcessor token = someone();
        String id = createShow(token, "Com histórico");

        // What episode approval will write once the review slice exists (spec 0003).
        UUID episodeId = UUID.randomUUID();
        jdbcClient.sql("update shows set voice_profile = cast(:profile as jsonb) where id = :id")
                .param("profile", """
                        {"toneDescription": "tom antigo",
                         "antiExamples": ["clickbait"],
                         "examples": [{"episodeId": "%s",
                                       "title": "Husserl e o demônio de Descartes",
                                       "description": "Sobre a epoché.",
                                       "approvedAt": "2026-07-15T10:00:00Z"}],
                         "updatedAt": "2026-07-15T10:00:00Z"}
                        """.formatted(episodeId))
                .param("id", UUID.fromString(id))
                .update();

        mockMvc.perform(get("/api/v1/shows/" + id + "/voice-profile").with(token))
                .andExpect(jsonPath("$.examples.length()").value(1))
                .andExpect(jsonPath("$.examples[0].episodeId").value(episodeId.toString()));

        mockMvc.perform(put("/api/v1/shows/" + id + "/voice-profile").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toneDescription": "tom novo", "antiExamples": []}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toneDescription").value("tom novo"))
                .andExpect(jsonPath("$.examples.length()").value(1))
                .andExpect(jsonPath("$.examples[0].title").value("Husserl e o demônio de Descartes"));

        String stored = jdbcClient.sql("select voice_profile::text from shows where id = :id")
                .param("id", UUID.fromString(id))
                .query(String.class)
                .single();
        assertThat(stored).contains("Husserl e o demônio de Descartes");
    }

    @Test
    @DisplayName("a voice profile update without both halves is rejected")
    void rejectsIncompleteVoiceProfileUpdate() throws Exception {
        RequestPostProcessor token = someone();
        String id = createShow(token, "Perfil incompleto");

        mockMvc.perform(put("/api/v1/shows/" + id + "/voice-profile").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toneDescription": "só o tom"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
