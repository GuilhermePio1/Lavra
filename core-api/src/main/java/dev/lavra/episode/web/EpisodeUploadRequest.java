package dev.lavra.episode.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * {@code EpisodeUploadRequest} of the REST contract.
 *
 * <p>{@code sizeBytes} carries no upper bound here on purpose: the contract
 * answers 413 for a file over the limit, and bean validation could only produce
 * a 400. The ceiling is enforced in the domain, where the refusal keeps its
 * meaning.
 */
record EpisodeUploadRequest(@NotNull UUID showId,
                            @NotBlank @Size(max = 255) String filename,
                            @NotBlank String contentType,
                            @NotNull @Positive Long sizeBytes,
                            @Size(max = 200) String title) {
}
