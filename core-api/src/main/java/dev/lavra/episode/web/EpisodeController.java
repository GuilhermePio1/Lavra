package dev.lavra.episode.web;

import dev.lavra.episode.EpisodeService;
import dev.lavra.episode.EpisodeUpload;
import dev.lavra.episode.domain.EpisodeStatus;
import dev.lavra.identity.IdentityService;
import dev.lavra.identity.domain.Account;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Episodes of the authenticated user, and the upload flow that creates them.
 *
 * <p>Three calls make an upload: register the episode and get a write ticket,
 * PUT the audio straight to the blob with it, then confirm. The audio itself
 * never reaches this controller — that is the whole point of ADR-0011.
 */
@RestController
@RequestMapping("/api/v1/episodes")
class EpisodeController {

    private final EpisodeService episodeService;
    private final IdentityService identityService;

    EpisodeController(EpisodeService episodeService, IdentityService identityService) {
        this.episodeService = episodeService;
        this.identityService = identityService;
    }

    @PostMapping
    ResponseEntity<EpisodeUploadTicketResponse> createEpisodeUpload(@AuthenticationPrincipal Jwt jwt,
                                                                    @Valid @RequestBody EpisodeUploadRequest request) {
        // The whole account: registering an episode is what the minute quota
        // gates (spec 0004).
        Account account = identityService.currentAccount(jwt);
        EpisodeUpload upload = episodeService.create(
                account,
                request.showId(),
                request.title(),
                request.filename(),
                request.contentType(),
                request.sizeBytes());

        return ResponseEntity
                .created(URI.create("/api/v1/episodes/" + upload.episode().getId()))
                .body(EpisodeUploadTicketResponse.from(upload));
    }

    @GetMapping
    EpisodeListResponse listEpisodes(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(required = false) UUID showId,
                                     @RequestParam(required = false) EpisodeStatus status,
                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return EpisodeListResponse.from(
                episodeService.list(callerId(jwt), showId, status, page, size));
    }

    @PostMapping("/{episodeId}/upload-ticket")
    EpisodeUploadTicketResponse renewEpisodeUploadTicket(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID episodeId) {
        return EpisodeUploadTicketResponse.from(
                episodeService.renewUploadTicket(episodeId, callerId(jwt)));
    }

    @PostMapping("/{episodeId}/upload-complete")
    EpisodeResponse completeEpisodeUpload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID episodeId) {
        return EpisodeResponse.from(episodeService.completeUpload(episodeId, callerId(jwt)));
    }

    @GetMapping("/{episodeId}")
    EpisodeDetailResponse getEpisode(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID episodeId) {
        return EpisodeDetailResponse.from(episodeService.get(episodeId, callerId(jwt)));
    }

    private UUID callerId(Jwt jwt) {
        return identityService.currentAccount(jwt).id();
    }
}
