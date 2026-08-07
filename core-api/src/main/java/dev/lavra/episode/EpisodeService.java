package dev.lavra.episode;

import dev.lavra.episode.domain.EpisodeStatus;
import dev.lavra.episode.domain.InvalidUploadDeclarationException;
import dev.lavra.episode.domain.UploadDeclaration;
import dev.lavra.episode.messaging.EpisodeUploadedPayload;
import dev.lavra.episode.persistence.EpisodeEntity;
import dev.lavra.episode.persistence.EpisodeQueries;
import dev.lavra.episode.persistence.EpisodeRepository;
import dev.lavra.episode.persistence.EpisodeStateTransitionEntity;
import dev.lavra.episode.persistence.EpisodeTransitionRepository;
import dev.lavra.identity.domain.Account;
import dev.lavra.identity.domain.UsageSnapshot;
import dev.lavra.shared.blob.BlobMetadata;
import dev.lavra.shared.blob.BlobStorage;
import dev.lavra.shared.blob.WriteTicket;
import dev.lavra.shared.messaging.EventPublisher;
import dev.lavra.shared.messaging.EventType;
import dev.lavra.shared.web.ApiError;
import dev.lavra.shared.web.InvalidRequestException;
import dev.lavra.shared.web.PlanLimitExceededException;
import dev.lavra.shared.web.ResourceConflictException;
import dev.lavra.shared.web.ResourceNotFoundException;
import dev.lavra.shared.web.UploadTooLargeException;
import dev.lavra.show.ShowService;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The upload half of the episode's life: registering one, authorising the
 * browser to write its audio, and confirming that the audio arrived — which is
 * what starts the pipeline (spec 0001, ADR-0011).
 *
 * <p>The raw audio never passes through here. This service issues a credential
 * scoped to one blob, and later asks storage what landed there; the bytes go
 * browser-to-blob and are the worker's problem from then on.
 *
 * <p>As in {@code ShowService}, ownership is a parameter of every method:
 * nothing is looked up by id alone, so a request for someone else's episode has
 * no path through this class.
 */
@Service
public class EpisodeService {

    private static final Logger log = LoggerFactory.getLogger(EpisodeService.class);

    /**
     * How long a write ticket lasts, as promised by the contract. Long enough
     * for a large file on a poor connection, short enough that a leaked URL
     * stops working the same afternoon — and an upload that outlives it asks for
     * a new one and resumes its remaining blocks.
     */
    static final Duration TICKET_TTL = Duration.ofHours(2);

    private final EpisodeRepository episodeRepository;
    private final EpisodeTransitionRepository transitionRepository;
    private final EpisodeQueries episodeQueries;
    private final ShowService showService;
    private final BlobStorage blobStorage;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    EpisodeService(EpisodeRepository episodeRepository,
                   EpisodeTransitionRepository transitionRepository,
                   EpisodeQueries episodeQueries,
                   ShowService showService,
                   BlobStorage blobStorage,
                   EventPublisher eventPublisher,
                   Clock clock) {
        this.episodeRepository = episodeRepository;
        this.transitionRepository = transitionRepository;
        this.episodeQueries = episodeQueries;
        this.showService = showService;
        this.blobStorage = blobStorage;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Registers an episode and hands back the credential to upload its audio.
     *
     * <p>The quota is checked here and only here: minutes are debited when the
     * worker reports what it processed, so at this point the balance is the
     * previous debits. An upload started with the last minute of the month runs
     * to completion and leaves the balance negative — the single overdraft of
     * spec 0004 — because aborting work already paid for helps nobody.
     */
    @Transactional
    public EpisodeUpload create(Account account, UUID showId, String workingTitle,
                                String filename, String contentType, long sizeBytes) {
        showService.requireOwned(showId, account.id());

        UsageSnapshot usage = account.usage();
        if (!usage.hasProcessedMinutesBalance()) {
            throw new PlanLimitExceededException(
                    ApiError.PLAN_QUOTA_EXCEEDED,
                    "Plan %s allows %d processed minute(s) per month; you have used %d."
                            .formatted(account.plan().code(), usage.processedMinutesLimit(), usage.processedMinutesUsed()));
        }

        UploadDeclaration declaration = declare(filename, contentType, sizeBytes);
        EpisodeEntity episode = episodeRepository.save(
                new EpisodeEntity(showId, workingTitle, declaration, clock.instant()));

        // Issued after the episode exists, and inside the transaction: storage
        // being unreachable rolls the episode back rather than leaving a row
        // nobody can ever upload to.
        return new EpisodeUpload(episode, ticketFor(episode));
    }

    /**
     * Reissues the write ticket for an upload still in progress. Neither
     * recreates the episode nor rechecks the quota — the blob path is the same
     * one, so the blocks already sent stay where they are.
     */
    @Transactional(readOnly = true)
    public EpisodeUpload renewUploadTicket(UUID episodeId, UUID userId) {
        EpisodeEntity episode = ownedEpisode(episodeId, userId);
        if (!episode.getStatus().isAwaitingUpload()) {
            throw new ResourceConflictException(
                    ApiError.EPISODE_NOT_AWAITING_UPLOAD,
                    "Episode %s is %s and is no longer accepting an upload."
                            .formatted(episodeId, episode.getStatus()));
        }
        return new EpisodeUpload(episode, ticketFor(episode));
    }

    /**
     * Confirms the upload and starts the pipeline.
     *
     * <p>Idempotent by design: the browser may retry this call, and an episode
     * already past {@code PENDING_UPLOAD} answers with its current state instead
     * of publishing a second event. A failed episode is the one state that
     * refuses — its upload will not be confirmed retroactively.
     *
     * <p>The event is published last, after every write of this transaction. A
     * broker that refuses the message rolls the whole thing back, so the client
     * can simply call again; the residual risk is the mirror case — published,
     * then the commit fails — which the worker absorbs by being idempotent on
     * {@code episodeId}, as at-least-once delivery requires of it anyway.
     */
    @Transactional
    public EpisodeEntity completeUpload(UUID episodeId, UUID userId) {
        EpisodeEntity episode = ownedEpisode(episodeId, userId);

        if (episode.getStatus() == EpisodeStatus.FAILED) {
            throw new ResourceConflictException(
                    ApiError.EPISODE_FAILED,
                    "Episode %s has failed; its upload cannot be confirmed.".formatted(episodeId));
        }
        if (!episode.getStatus().isAwaitingUpload()) {
            return episode;
        }

        BlobMetadata blob = blobStorage.describe(episode.getSourceBlobPath())
                .orElseThrow(() -> new InvalidRequestException(
                        ApiError.UPLOAD_NOT_FOUND,
                        "No audio found at " + episode.getSourceBlobPath()));

        if (!episode.matchesDeclaration(blob.sizeBytes(), blob.contentType())) {
            throw new InvalidRequestException(
                    ApiError.UPLOAD_MISMATCH,
                    "Uploaded blob is %d byte(s) of %s; %d byte(s) of %s were declared."
                            .formatted(blob.sizeBytes(), blob.contentType(),
                                    episode.getSizeBytes(), episode.getContentType()));
        }

        transition(episode, EpisodeStatus.RECEIVED, "upload confirmed");
        transition(episode, EpisodeStatus.AUDIO_PROCESSING, "handed to the media worker");

        UUID eventId = eventPublisher.publish(EventType.EPISODE_UPLOADED_V1, new EpisodeUploadedPayload(
                episode.getId(),
                episode.getShowId(),
                episode.getSourceBlobPath(),
                episode.getOriginalFilename(),
                episode.getContentType()));
        log.info("Episode {} handed to the pipeline as event {}", episode.getId(), eventId);

        return episode;
    }

    @Transactional(readOnly = true)
    public EpisodeWithHistory get(UUID episodeId, UUID userId) {
        EpisodeEntity episode = ownedEpisode(episodeId, userId);
        return new EpisodeWithHistory(
                episode, transitionRepository.findByEpisodeIdOrderByOccurredAtAsc(episodeId));
    }

    /**
     * A page of the user's episodes.
     *
     * @param showId optional filter; a show that is not the user's is a 404,
     *               exactly as asking for it directly would be — the filter must
     *               not become a way to probe for other people's shows
     */
    @Transactional(readOnly = true)
    public EpisodePage list(UUID userId, UUID showId, EpisodeStatus status, int page, int size) {
        if (showId != null) {
            showService.requireOwned(showId, userId);
        }
        return episodeQueries.page(userId, showId, status, page, size);
    }

    /** Moves the episode and records the move in the same breath. */
    private void transition(EpisodeEntity episode, EpisodeStatus next, String reason) {
        EpisodeStatus previous = episode.getStatus();
        episode.transitionTo(next);
        transitionRepository.save(new EpisodeStateTransitionEntity(
                episode.getId(), previous, next, reason, clock.instant()));
    }

    private WriteTicket ticketFor(EpisodeEntity episode) {
        return blobStorage.issueWriteTicket(episode.getSourceBlobPath(), TICKET_TTL);
    }

    /**
     * Translates the domain's refusal into the status the contract promises: a
     * size over the limit is a 413, anything else about the declaration is a
     * 400. The domain says why, the boundary decides what that costs.
     */
    private UploadDeclaration declare(String filename, String contentType, long sizeBytes) {
        try {
            return UploadDeclaration.of(filename, contentType, sizeBytes);
        } catch (InvalidUploadDeclarationException rejected) {
            throw switch (rejected.rejection()) {
                case TOO_LARGE -> new UploadTooLargeException(rejected.getMessage());
                case UNSUPPORTED_CONTENT_TYPE, EXTENSION_MISMATCH ->
                        new InvalidRequestException(ApiError.VALIDATION_ERROR, rejected.getMessage());
            };
        }
    }

    /**
     * The one lookup in the slice. An episode that does not exist and one owned
     * by someone else fail identically, so the API never confirms that another
     * user's episode exists (spec 0004).
     */
    private EpisodeEntity ownedEpisode(UUID episodeId, UUID userId) {
        return episodeRepository.findOwned(episodeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode " + episodeId + " not found"));
    }
}
