package dev.lavra.show.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * {@code ShowVoiceProfileUpdate} of the REST contract: a full replacement of the
 * editable half, so both fields are required — omitting one would mean erasing
 * it, and the contract asks for that to be explicit.
 */
record VoiceProfileUpdateRequest(@NotNull @Size(max = 1000) String toneDescription,
                                 @NotNull @Size(max = 20) List<@Size(max = 200) String> antiExamples) {
}
