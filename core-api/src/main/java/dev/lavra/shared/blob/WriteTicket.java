package dev.lavra.shared.blob;

import java.net.URI;
import java.time.Instant;

/**
 * A time-boxed permission to write one blob, handed to the browser
 * ({@code EpisodeUploadTicket.upload} in the REST contract).
 *
 * @param url       the blob URL with the credential attached; write-only, single blob
 * @param blobPath  container-relative path the credential is bound to
 * @param expiresAt when the credential stops working — an upload still running
 *                  by then asks for a new one and resumes its remaining blocks
 */
public record WriteTicket(URI url, String blobPath, Instant expiresAt) {
}
