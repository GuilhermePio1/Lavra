package dev.lavra.show.web;

import dev.lavra.show.domain.VoiceProfile;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code ShowVoiceProfile} of the REST contract. The examples are exposed but
 * never accepted back: they are read-only in the contract and grow only through
 * episode approval (spec 0003).
 */
record VoiceProfileResponse(String toneDescription,
                            List<String> antiExamples,
                            List<ExampleView> examples,
                            Instant updatedAt) {

    record ExampleView(UUID episodeId, String title, String description, Instant approvedAt) {
    }

    static VoiceProfileResponse from(VoiceProfile profile) {
        return new VoiceProfileResponse(
                profile.toneDescription(),
                profile.antiExamples(),
                profile.examples().stream()
                        .map(example -> new ExampleView(
                                example.episodeId(),
                                example.title(),
                                example.description(),
                                example.approvedAt()))
                        .toList(),
                profile.updatedAt());
    }
}
