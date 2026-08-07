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
 *
 * <p>Failures arrive as {@link BlobAccessException} and never as an SDK type:
 * the port would be a leaky one if reasoning about storage failing required
 * importing {@code com.azure}. Callers are not expected to catch it — there is
 * no compensation for storage being down, and letting it reach the exception
 * handler is the correct 500. It is declared so that the contract is stated
 * rather than discovered, and so that a retry policy has a type to key on.
 */
public interface BlobStorage {

    /**
     * Issues a credential that permits writing one single blob, and nothing
     * else: no read, no list, no other path, and only until it expires.
     *
     * <p>Signing is local — an HMAC over the account key, computed in this
     * process — so issuing a ticket does not depend on storage being reachable.
     * The one exception is the development convenience of creating the
     * container on first use ({@code lavra.blob.auto-create-container}), which
     * is the only thing here that touches the network.
     *
     * @param blobPath container-relative path the credential is bound to
     * @param ttl      how long the credential stays valid
     * @throws BlobAccessException if the container has to be created and
     *                             storage cannot be reached
     */
    WriteTicket issueWriteTicket(String blobPath, Duration ttl);

    /**
     * Reads the blob's metadata. Used to confirm that an upload actually landed
     * before the pipeline starts — the bytes themselves are the worker's
     * problem.
     *
     * @return the metadata, or empty if the blob is not there; absence is an
     *         answer, not a failure, and is the expected result while the
     *         browser is still uploading
     * @throws BlobAccessException if storage cannot be reached, or answers
     *                             anything other than "not found"
     */
    Optional<BlobMetadata> describe(String blobPath);

    /**
     * Removes a blob if it exists; does nothing if it does not.
     *
     * @throws BlobAccessException if storage cannot be reached or refuses the
     *                             delete
     */
    void delete(String blobPath);
}
