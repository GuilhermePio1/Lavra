package dev.lavra.episode.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The audio formats an episode may be uploaded in, each one pairing the
 * {@code Content-Type} the client declares with the extension the blob is
 * stored under.
 *
 * <p>Both halves are needed and they have to agree. The REST contract says only
 * the extension of the original filename is used in the blob path, and the
 * declared content type is what the browser will stamp on the blob — a request
 * where the two disagree describes an upload that cannot be validated later, so
 * it is refused before the episode exists.
 *
 * <p>WAV is the reason this is not a plain map: it travels under two content
 * types in the wild and both land on the same extension.
 */
public enum AudioFormat {

    MP3("mp3", "audio/mpeg"),
    WAV("wav", "audio/wav", "audio/x-wav"),
    M4A("m4a", "audio/mp4"),
    FLAC("flac", "audio/flac");

    private final String extension;
    private final List<String> contentTypes;

    AudioFormat(String extension, String... contentTypes) {
        this.extension = extension;
        this.contentTypes = List.of(contentTypes);
    }

    public String extension() {
        return extension;
    }

    /** The format that declares this content type, if it is one we accept. */
    public static Optional<AudioFormat> ofContentType(String contentType) {
        if (contentType == null) {
            return Optional.empty();
        }
        String normalised = contentType.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(format -> format.contentTypes.contains(normalised))
                .findFirst();
    }

    /**
     * Whether {@code filename} ends in this format's extension.
     *
     * <p>A file with no extension, or one that contradicts the declared content
     * type, fails here — the alternative is trusting the content type alone and
     * storing {@code original.mp3} holding a FLAC.
     */
    public boolean matchesFilename(String filename) {
        return filename != null
                && filename.toLowerCase(Locale.ROOT).endsWith("." + extension);
    }
}
