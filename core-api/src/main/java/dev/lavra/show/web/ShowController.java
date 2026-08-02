package dev.lavra.show.web;

import dev.lavra.identity.IdentityService;
import dev.lavra.identity.domain.Account;
import dev.lavra.show.ShowService;
import dev.lavra.show.ShowWithEpisodeCount;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shows of the authenticated user.
 *
 * <p>Every route resolves the caller through {@link IdentityService} before
 * touching a show, which is also what provisions a brand-new user who lands
 * here before ever calling {@code /me}.
 */
@RestController
@RequestMapping("/api/v1/shows")
class ShowController {

    private final ShowService showService;
    private final IdentityService identityService;

    ShowController(ShowService showService, IdentityService identityService) {
        this.showService = showService;
        this.identityService = identityService;
    }

    @GetMapping
    ShowListResponse listShows(@AuthenticationPrincipal Jwt jwt) {
        return new ShowListResponse(showService.list(callerId(jwt)).stream()
                .map(ShowResponse::from)
                .toList());
    }

    @PostMapping
    ResponseEntity<ShowResponse> createShow(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody ShowCreateRequest request) {
        // The whole account, not just the id: creating a show is the one route
        // here that has to weigh the plan's allowance.
        Account account = identityService.currentAccount(jwt);
        ShowWithEpisodeCount created = showService.create(account, request.name(), request.description());

        return ResponseEntity
                .created(URI.create("/api/v1/shows/" + created.show().getId()))
                .body(ShowResponse.from(created));
    }

    @GetMapping("/{showId}")
    ShowResponse getShow(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID showId) {
        return ShowResponse.from(showService.get(showId, callerId(jwt)));
    }

    @PatchMapping("/{showId}")
    ShowResponse updateShow(@AuthenticationPrincipal Jwt jwt,
                            @PathVariable UUID showId,
                            @Valid @RequestBody ShowPatchRequest request) {
        return ShowResponse.from(
                showService.update(showId, callerId(jwt), request.name(), request.description()));
    }

    @DeleteMapping("/{showId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteShow(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID showId) {
        showService.delete(showId, callerId(jwt));
    }

    @GetMapping("/{showId}/voice-profile")
    VoiceProfileResponse getVoiceProfile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID showId) {
        return VoiceProfileResponse.from(showService.voiceProfile(showId, callerId(jwt)));
    }

    @PutMapping("/{showId}/voice-profile")
    VoiceProfileResponse updateVoiceProfile(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID showId,
                                            @Valid @RequestBody VoiceProfileUpdateRequest request) {
        return VoiceProfileResponse.from(showService.replaceVoiceProfile(
                showId, callerId(jwt), request.toneDescription(), request.antiExamples()));
    }

    private UUID callerId(Jwt jwt) {
        return identityService.currentAccount(jwt).id();
    }
}
