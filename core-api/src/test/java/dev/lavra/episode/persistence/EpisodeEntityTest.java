package dev.lavra.episode.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.lavra.episode.domain.EpisodeStatus;
import dev.lavra.episode.domain.IllegalEpisodeTransitionException;
import dev.lavra.episode.domain.UploadDeclaration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The entity enforcing the state machine on itself, without a database. */
class EpisodeEntityTest {

    @Test
    @DisplayName("a refused transition names the episode and leaves it where it was")
    void refusedTransitionNamesTheEpisode() {
        EpisodeEntity episode = new EpisodeEntity(
                UUID.randomUUID(),
                "Husserl e o demônio",
                UploadDeclaration.of("episodio.mp3", "audio/mpeg", 1_024),
                Instant.now());

        assertThatThrownBy(() -> episode.transitionTo(EpisodeStatus.READY))
                .isInstanceOf(IllegalEpisodeTransitionException.class)
                // The id is the point: this exception is a bug reaching a log,
                // and the log has nothing else to identify the row with.
                .hasMessageContaining(episode.getId().toString())
                .hasMessageContaining("PENDING_UPLOAD")
                .hasMessageContaining("READY");

        assertThat(episode.getStatus()).isEqualTo(EpisodeStatus.PENDING_UPLOAD);
    }
}
