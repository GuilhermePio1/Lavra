package dev.lavra.episode;

import dev.lavra.episode.persistence.EpisodeEntity;
import dev.lavra.shared.blob.WriteTicket;

/**
 * An episode waiting for its bytes, together with the credential that may write
 * them — the {@code EpisodeUploadTicket} of the contract.
 *
 * <p>The two travel as one because they are only ever useful together: the
 * ticket alone says nothing about which episode it belongs to, and an episode in
 * {@code PENDING_UPLOAD} without a ticket is a dead end for the client.
 */
public record EpisodeUpload(EpisodeEntity episode, WriteTicket ticket) {
}
