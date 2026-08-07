package dev.lavra.episode.domain;

import static dev.lavra.episode.domain.EpisodeStatus.AUDIO_PROCESSING;
import static dev.lavra.episode.domain.EpisodeStatus.FAILED;
import static dev.lavra.episode.domain.EpisodeStatus.GENERATING;
import static dev.lavra.episode.domain.EpisodeStatus.IN_REVIEW;
import static dev.lavra.episode.domain.EpisodeStatus.PENDING_UPLOAD;
import static dev.lavra.episode.domain.EpisodeStatus.READY;
import static dev.lavra.episode.domain.EpisodeStatus.RECEIVED;
import static dev.lavra.episode.domain.EpisodeStatus.TRANSCRIBING;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The state machine of spec 0001, checked without a database or a Spring context. */
class EpisodeStatusTest {

    @Test
    @DisplayName("the pipeline advances one step at a time, in the order of the spec")
    void advancesAlongTheHappyPath() {
        assertThat(PENDING_UPLOAD.canTransitionTo(RECEIVED)).isTrue();
        assertThat(RECEIVED.canTransitionTo(AUDIO_PROCESSING)).isTrue();
        assertThat(AUDIO_PROCESSING.canTransitionTo(TRANSCRIBING)).isTrue();
        assertThat(TRANSCRIBING.canTransitionTo(GENERATING)).isTrue();
        assertThat(GENERATING.canTransitionTo(IN_REVIEW)).isTrue();
        assertThat(IN_REVIEW.canTransitionTo(READY)).isTrue();
    }

    @Test
    @DisplayName("no state skips ahead")
    void refusesToSkipStages() {
        assertThat(PENDING_UPLOAD.canTransitionTo(AUDIO_PROCESSING)).isFalse();
        assertThat(RECEIVED.canTransitionTo(TRANSCRIBING)).isFalse();
        assertThat(AUDIO_PROCESSING.canTransitionTo(IN_REVIEW)).isFalse();
        assertThat(GENERATING.canTransitionTo(READY)).isFalse();
    }

    @Test
    @DisplayName("nothing moves backwards, and READY is the end of the line")
    void refusesToGoBackwards() {
        assertThat(RECEIVED.canTransitionTo(PENDING_UPLOAD)).isFalse();
        assertThat(IN_REVIEW.canTransitionTo(GENERATING)).isFalse();
        assertThat(Arrays.stream(EpisodeStatus.values()).noneMatch(READY::canTransitionTo)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(names = {"RECEIVED", "AUDIO_PROCESSING", "TRANSCRIBING", "GENERATING"})
    @DisplayName("any stage that is actually processing can fail")
    void failsFromAnyProcessingStage(EpisodeStatus stage) {
        assertThat(stage.canTransitionTo(FAILED)).isTrue();
    }

    @Test
    @DisplayName("an upload nobody finishes is not a failure: the cleanup job sweeps it")
    void doesNotFailWhileAwaitingUpload() {
        assertThat(PENDING_UPLOAD.canTransitionTo(FAILED)).isFalse();
    }

    @Test
    @DisplayName("a reviewed or approved episode has nothing left to fail at")
    void doesNotFailAfterTheWorkIsDone() {
        assertThat(IN_REVIEW.canTransitionTo(FAILED)).isFalse();
        assertThat(READY.canTransitionTo(FAILED)).isFalse();
    }

    @Test
    @DisplayName("reprocessing restarts a failed episode at the audio stage, not at the upload")
    void reprocessesFromFailed() {
        assertThat(FAILED.canTransitionTo(AUDIO_PROCESSING)).isTrue();
        assertThat(FAILED.canTransitionTo(PENDING_UPLOAD)).isFalse();
        assertThat(FAILED.canTransitionTo(RECEIVED)).isFalse();
        assertThat(FAILED.canTransitionTo(READY)).isFalse();
    }

    @Test
    @DisplayName("only PENDING_UPLOAD is still waiting for audio")
    void knowsWhichStateIsStillWaitingForAudio() {
        assertThat(PENDING_UPLOAD.isAwaitingUpload()).isTrue();
        assertThat(Arrays.stream(EpisodeStatus.values())
                .filter(EpisodeStatus::isAwaitingUpload)
                .count()).isEqualTo(1);
    }
}
