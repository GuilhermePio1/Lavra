package dev.lavra.show.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rule that matters here — an edit never touches the accumulated examples —
 * is plain Java and tested as such: no Spring context, no database (ADR-0013).
 */
class VoiceProfileTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    private static VoiceExample approvedExample() {
        return new VoiceExample(
                UUID.randomUUID(),
                "Husserl e o demônio de Descartes",
                "Sobre a epoché e o que sobra quando você suspende o mundo.",
                Instant.parse("2026-07-15T10:00:00Z"));
    }

    @Test
    @DisplayName("a new show starts with a blank profile, never with nulls")
    void emptyProfileHasNoNulls() {
        VoiceProfile profile = VoiceProfile.empty();

        assertThat(profile.toneDescription()).isEmpty();
        assertThat(profile.antiExamples()).isEmpty();
        assertThat(profile.examples()).isEmpty();
        assertThat(profile.updatedAt()).isNull();
    }

    @Test
    @DisplayName("a profile read back from an empty JSON document behaves like a blank one")
    void normalisesNullsFromStorage() {
        VoiceProfile profile = new VoiceProfile(null, null, null, null);

        assertThat(profile.toneDescription()).isEmpty();
        assertThat(profile.antiExamples()).isEmpty();
        assertThat(profile.examples()).isEmpty();
    }

    @Test
    @DisplayName("an example approved without a description still reads back — the title alone is usable few-shot material")
    void normalisesAMissingExampleDescription() {
        VoiceExample example = new VoiceExample(UUID.randomUUID(), "Husserl e o demônio de Descartes", null, NOW);

        assertThat(example.description()).isEmpty();
    }

    @Test
    @DisplayName("editing replaces tone and anti-examples and stamps the edit")
    void replacesTheEditableParts() {
        VoiceProfile edited = VoiceProfile.empty()
                .withEditableParts("irreverente, filosófico, primeira pessoa", List.of("Neste episódio, exploramos…"), NOW);

        assertThat(edited.toneDescription()).isEqualTo("irreverente, filosófico, primeira pessoa");
        assertThat(edited.antiExamples()).containsExactly("Neste episódio, exploramos…");
        assertThat(edited.updatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("editing carries the approved examples across — they are history, not a field of the request")
    void keepsExamplesAcrossAnEdit() {
        VoiceExample example = approvedExample();
        VoiceProfile withHistory = new VoiceProfile("tom antigo", List.of("clickbait"), List.of(example), NOW);

        VoiceProfile edited = withHistory.withEditableParts("tom novo", List.of(), NOW.plusSeconds(60));

        assertThat(edited.examples()).containsExactly(example);
        assertThat(edited.toneDescription()).isEqualTo("tom novo");
        assertThat(edited.antiExamples()).isEmpty();
    }

    @Test
    @DisplayName("the profile does not share mutable state with whoever built it")
    void copiesTheListsItIsGiven() {
        List<String> antiExamples = new ArrayList<>(List.of("clickbait"));
        VoiceProfile profile = new VoiceProfile("tom", antiExamples, List.of(), NOW);

        antiExamples.add("outro padrão vetado");

        assertThat(profile.antiExamples()).containsExactly("clickbait");
    }
}
