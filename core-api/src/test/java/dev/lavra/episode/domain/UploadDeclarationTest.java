package dev.lavra.episode.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.lavra.episode.domain.UploadDeclaration.Rejection;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** What the API accepts as a description of an upload, before any byte exists. */
class UploadDeclarationTest {

    @ParameterizedTest
    @CsvSource({
            "episodio.mp3,  audio/mpeg, mp3",
            "episodio.wav,  audio/wav,  wav",
            "episodio.wav,  audio/x-wav, wav",
            "episodio.m4a,  audio/mp4,  m4a",
            "episodio.flac, audio/flac, flac"
    })
    @DisplayName("every accepted format lands on the extension its blob is stored under")
    void acceptsEveryFormatOfTheContract(String filename, String contentType, String extension) {
        UploadDeclaration declaration = UploadDeclaration.of(filename, contentType, 1_024);

        assertThat(declaration.format().extension()).isEqualTo(extension);
        assertThat(declaration.contentType()).isEqualTo(contentType);
    }

    @Test
    @DisplayName("the blob path is derived from the episode, not from the name the client sent")
    void derivesTheBlobPathFromTheEpisode() {
        UploadDeclaration declaration = UploadDeclaration.of("../../etc/passwd.mp3", "audio/mpeg", 1_024);
        UUID episodeId = UUID.randomUUID();

        assertThat(EpisodeBlobLayout.rawAudio(episodeId, declaration.format()))
                .isEqualTo("raw/" + episodeId + "/original.mp3");
    }

    @Test
    @DisplayName("an extension that contradicts the declared type is refused")
    void refusesExtensionThatContradictsTheContentType() {
        assertThatThrownBy(() -> UploadDeclaration.of("episodio.flac", "audio/mpeg", 1_024))
                .isInstanceOf(InvalidUploadDeclarationException.class)
                .extracting(ex -> ((InvalidUploadDeclarationException) ex).rejection())
                .isEqualTo(Rejection.EXTENSION_MISMATCH);
    }

    @Test
    @DisplayName("a file with no extension has nothing to store it under")
    void refusesFilenameWithoutExtension() {
        assertThatThrownBy(() -> UploadDeclaration.of("episodio", "audio/mpeg", 1_024))
                .isInstanceOf(InvalidUploadDeclarationException.class);
    }

    @Test
    @DisplayName("video and anything else outside the list are refused as a content type")
    void refusesUnsupportedContentType() {
        assertThatThrownBy(() -> UploadDeclaration.of("episodio.mp4", "video/mp4", 1_024))
                .isInstanceOf(InvalidUploadDeclarationException.class)
                .extracting(ex -> ((InvalidUploadDeclarationException) ex).rejection())
                .isEqualTo(Rejection.UNSUPPORTED_CONTENT_TYPE);
    }

    @Test
    @DisplayName("casing and stray whitespace do not decide whether an upload is accepted")
    void normalisesTheDeclaredContentType() {
        UploadDeclaration declaration = UploadDeclaration.of("EPISODIO.MP3", " Audio/MPEG ", 1_024);

        assertThat(declaration.format()).isEqualTo(AudioFormat.MP3);
        assertThat(declaration.contentType()).isEqualTo("audio/mpeg");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -500L})
    @DisplayName("a size that is not positive is a caller's bug, not an upload the API can refuse")
    void refusesNonPositiveSize(long sizeBytes) {
        assertThatThrownBy(() -> UploadDeclaration.of("episodio.mp3", "audio/mpeg", sizeBytes))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(InvalidUploadDeclarationException.class);
    }

    @Test
    @DisplayName("the size guards hold for a declaration built without the factory")
    void guardsSizeOnTheCanonicalConstructor() {
        assertThatThrownBy(() ->
                new UploadDeclaration("episodio.mp3", "audio/mpeg", AudioFormat.MP3, -500))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new UploadDeclaration(
                "episodio.mp3", "audio/mpeg", AudioFormat.MP3, UploadDeclaration.MAX_SIZE_BYTES + 1))
                .isInstanceOf(InvalidUploadDeclarationException.class)
                .extracting(ex -> ((InvalidUploadDeclarationException) ex).rejection())
                .isEqualTo(Rejection.TOO_LARGE);
    }

    @Test
    @DisplayName("the size limit is exactly 2 GB: at the limit passes, one byte over does not")
    void refusesDeclarationOverTheSizeLimit() {
        assertThat(UploadDeclaration.of("episodio.mp3", "audio/mpeg", UploadDeclaration.MAX_SIZE_BYTES).sizeBytes())
                .isEqualTo(UploadDeclaration.MAX_SIZE_BYTES);

        assertThatThrownBy(() ->
                UploadDeclaration.of("episodio.mp3", "audio/mpeg", UploadDeclaration.MAX_SIZE_BYTES + 1))
                .isInstanceOf(InvalidUploadDeclarationException.class)
                .extracting(ex -> ((InvalidUploadDeclarationException) ex).rejection())
                .isEqualTo(Rejection.TOO_LARGE);
    }
}
