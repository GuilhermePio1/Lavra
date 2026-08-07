package dev.lavra.episode.web;

import dev.lavra.episode.EpisodeUpload;
import dev.lavra.shared.blob.WriteTicket;
import java.net.URI;
import java.time.Instant;

/**
 * {@code EpisodeUploadTicket} of the REST contract: the episode that was just
 * registered, plus where and until when its audio may be written.
 */
record EpisodeUploadTicketResponse(EpisodeResponse episode, Upload upload) {

    /**
     * @param url       write-only credential, bound to this single blob
     * @param blobPath  container-relative path the credential covers
     * @param expiresAt when it stops working; a longer upload renews it and
     *                  resumes rather than restarting
     */
    record Upload(URI url, String blobPath, Instant expiresAt) {
    }

    static EpisodeUploadTicketResponse from(EpisodeUpload upload) {
        WriteTicket ticket = upload.ticket();
        return new EpisodeUploadTicketResponse(
                EpisodeResponse.from(upload.episode()),
                new Upload(ticket.url(), ticket.blobPath(), ticket.expiresAt()));
    }
}
