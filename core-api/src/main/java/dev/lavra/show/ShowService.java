package dev.lavra.show;

import dev.lavra.identity.domain.Account;
import dev.lavra.identity.domain.UsageSnapshot;
import dev.lavra.shared.web.ApiError;
import dev.lavra.shared.web.PlanLimitExceededException;
import dev.lavra.shared.web.ResourceConflictException;
import dev.lavra.shared.web.ResourceNotFoundException;
import dev.lavra.show.domain.VoiceProfile;
import dev.lavra.show.persistence.ShowEntity;
import dev.lavra.show.persistence.ShowQueries;
import dev.lavra.show.persistence.ShowRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The show aggregate: creation against the plan's show allowance, strict
 * ownership on every read and write, and the editable half of the voice
 * profile.
 *
 * <p>Every method takes the owner explicitly. Nothing here looks a show up by
 * id alone, so the request that would reach another user's show has no path
 * through this class.
 */
@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final ShowQueries showQueries;
    private final Clock clock;

    ShowService(ShowRepository showRepository, ShowQueries showQueries, Clock clock) {
        this.showRepository = showRepository;
        this.showQueries = showQueries;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ShowWithEpisodeCount> list(UUID userId) {
        List<ShowEntity> shows = showRepository.findByUserIdOrderByCreatedAtDesc(userId);
        // One aggregate query for the whole list instead of one per show.
        Map<UUID, Long> counts = showQueries.episodeCountsByShow(userId);
        return shows.stream()
                .map(show -> new ShowWithEpisodeCount(show, counts.getOrDefault(show.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ShowWithEpisodeCount get(UUID showId, UUID userId) {
        ShowEntity show = ownedShow(showId, userId);
        return new ShowWithEpisodeCount(show, showQueries.countEpisodes(showId));
    }

    /**
     * Creates a show if the plan still has room for one.
     *
     * <p>The allowance is read from the account resolved for this request, so a
     * plan change takes effect on the very next call — entitlements are never
     * cached across it (spec 0004). Two simultaneous creates could both pass the
     * check and land one show over the limit; the next create is refused and the
     * cost of a transient extra show is a row, so the check stays a plain read.
     */
    @Transactional
    public ShowWithEpisodeCount create(Account account, String name, String description) {
        UsageSnapshot usage = account.usage();
        if (!usage.canCreateShow()) {
            throw new PlanLimitExceededException(
                    ApiError.PLAN_SHOW_LIMIT_EXCEEDED,
                    "Plan %s allows %d show(s); you already have %d."
                            .formatted(account.plan().code(), usage.showsLimit(), usage.showsUsed()));
        }

        ShowEntity show = showRepository.save(
                new ShowEntity(account.id(), name, description, clock.instant()));
        return new ShowWithEpisodeCount(show, 0);
    }

    @Transactional
    public ShowWithEpisodeCount update(UUID showId, UUID userId, String name, String description) {
        ShowEntity show = ownedShow(showId, userId);
        show.applyPatch(name, description);
        return new ShowWithEpisodeCount(show, showQueries.countEpisodes(showId));
    }

    /**
     * Removes a show, but only an empty one: the database would cascade to
     * episodes, taking audio, transcripts and the accumulated voice profile with
     * it. Emptying the show first has to be a deliberate act.
     */
    @Transactional
    public void delete(UUID showId, UUID userId) {
        ShowEntity show = ownedShow(showId, userId);
        if (showQueries.hasEpisodes(showId)) {
            throw new ResourceConflictException(
                    ApiError.SHOW_NOT_EMPTY,
                    "Show still has episodes; delete them before deleting the show.");
        }
        showRepository.delete(show);
    }

    @Transactional(readOnly = true)
    public VoiceProfile voiceProfile(UUID showId, UUID userId) {
        return ownedShow(showId, userId).voiceProfile();
    }

    /**
     * Replaces tone and anti-examples wholesale. The approved examples survive:
     * they are the show's history, not a field of this request (spec 0003).
     */
    @Transactional
    public VoiceProfile replaceVoiceProfile(UUID showId, UUID userId, String toneDescription, List<String> antiExamples) {
        ShowEntity show = ownedShow(showId, userId);
        show.replaceEditableVoiceProfile(toneDescription, antiExamples, clock.instant());
        return show.voiceProfile();
    }

    /**
     * The one lookup in the slice. A show that does not exist and a show owned
     * by someone else fail identically — 404 in both cases, so the API never
     * confirms that another user's show exists (spec 0004).
     */
    private ShowEntity ownedShow(UUID showId, UUID userId) {
        return showRepository.findByIdAndUserId(showId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Show " + showId + " not found"));
    }
}
