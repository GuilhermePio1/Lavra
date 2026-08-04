package dev.lavra.shared.blob;

import java.time.Duration;
import java.util.Optional;

/**
 * Port to Blob Storage (ADR-0013). The raw audio never travels through this
 * service: the browser writes it straight to the blob with a credential issued
 * here, and the API only authorises, inspects and cleans up (ADR-0011).
 *
 * <p>Paths are always container-relative — {@code raw/{episodeId}/original.mp3},
 * never a full URL. The container is configuration, not something a caller gets
 * to choose.
 */
public interface BlobStorage {

    /**
     * Issues a credential that permits writing one single blob, and nothing
     * else: no read, no list, no other path, and only until it expires.
     *
     * @param blobPath container-relative path the credential is bound to
     * @param ttl      how long the credential stays valid
     */
    WriteTicket issueWriteTicket(String blobPath, Duration ttl);

    /**
     * Reads the blob's metadata, or empty if it is not there. Used to confirm
     * that an upload actually landed before the pipeline starts — the bytes
     * themselves are the worker's problem.
     */
    Optional<BlobMetadata> describe(String blobPath);

    /** Removes a blob if it exists; does nothing if it does not. */
    void delete(String blobPath);
}
