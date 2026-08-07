package dev.lavra.shared.blob;

import com.azure.core.exception.AzureException;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Blob Storage over the Azure SDK, talking to Azurite locally and to a real
 * storage account in Azure — the same code either way, only the connection
 * string changes.
 *
 * <p>This class is the boundary of the Azure SDK: every storage failure leaves
 * it as a {@link BlobAccessException}, so no SDK type is on the port's contract.
 */
class AzureBlobStorage implements BlobStorage {

    private static final int NOT_FOUND = 404;

    private final BlobContainerClient container;
    private final Clock clock;

    /**
     * Creating the container on first use is a development convenience: Azurite
     * starts empty, and doing it at startup would make every
     * {@code @SpringBootTest} in the codebase — most of which never touch a
     * blob — require Azurite to be running.
     *
     * <p>Off, the container is expected to already exist, which is what
     * infrastructure-as-code gives us in Azure. That is not only one HTTP
     * round-trip saved per ticket: it is what makes {@link #issueWriteTicket}
     * an operation that talks to nothing and therefore cannot fail on storage
     * being unreachable.
     */
    private final boolean autoCreateContainer;

    private volatile boolean containerEnsured;

    AzureBlobStorage(BlobContainerClient container, Clock clock, boolean autoCreateContainer) {
        this.container = container;
        this.clock = clock;
        this.autoCreateContainer = autoCreateContainer;
    }

    @Override
    public WriteTicket issueWriteTicket(String blobPath, Duration ttl) {
        ensureContainer();

        BlobClient blob = container.getBlobClient(blobPath);
        Instant expiresAt = clock.instant().plus(ttl);

        // Write and create, nothing else. No read and no list means a leaked
        // credential cannot be used to fetch the audio back or to discover what
        // else exists in the container (ADR-0011).
        BlobSasPermission permission = new BlobSasPermission()
                .setWritePermission(true)
                .setCreatePermission(true);

        String credential = sign(blob, new BlobServiceSasSignatureValues(
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC), permission));

        return new WriteTicket(URI.create(blob.getBlobUrl() + "?" + credential), blobPath, expiresAt);
    }

    @Override
    public Optional<BlobMetadata> describe(String blobPath) {
        return call("describe '" + blobPath + "'", () -> {
            try {
                BlobProperties properties = container.getBlobClient(blobPath).getProperties();
                return Optional.of(new BlobMetadata(blobPath, properties.getBlobSize(), properties.getContentType()));
            } catch (BlobStorageException e) {
                // The blob not being there is an answer, not a failure: it is
                // the expected result while the browser is still uploading.
                if (e.getStatusCode() == NOT_FOUND) {
                    return Optional.empty();
                }
                throw e;
            }
        });
    }

    @Override
    public void delete(String blobPath) {
        call("delete '" + blobPath + "'", () -> container.getBlobClient(blobPath).deleteIfExists());
    }

    /**
     * Signs the credential with the account key.
     *
     * <p>This one method is the whole of the "how it is signed" decision. Moving
     * to a user delegation SAS — signed by an OAuth key obtained with the
     * service's managed identity, so no account key exists in the process — is a
     * change here and nowhere else; callers and the port stay as they are. That
     * is the intended next step for production, and it is why the signing is not
     * inlined above.
     *
     * <p>Note that it is arithmetic and not a request: nothing is sent to
     * storage to obtain a SAS, which is why issuing a ticket needs no error
     * handling of its own.
     */
    private String sign(BlobClient blob, BlobServiceSasSignatureValues values) {
        return blob.generateSas(values);
    }

    private void ensureContainer() {
        if (autoCreateContainer && !containerEnsured) {
            call("create container '" + container.getBlobContainerName() + "'", container::createIfNotExists);
            containerEnsured = true;
        }
    }

    /** Runs one SDK call, translating a storage failure into the port's type. */
    private <T> T call(String attempted, Supplier<T> operation) {
        try {
            return operation.get();
        } catch (RuntimeException e) {
            if (!isStorageFailure(e)) {
                throw e;
            }
            throw new BlobAccessException("Blob storage failed to " + attempted, e);
        }
    }

    /**
     * What counts as storage failing, as opposed to this class being wrong.
     *
     * <p>Two of the three shapes are declared Azure types: {@link AzureException}
     * (with {@link BlobStorageException} under it) for an error the service
     * answered, and {@link UncheckedIOException} for one the HTTP client
     * reported. The third is undeclared and is the one that actually shows up
     * when Azure is unreachable — the synchronous storage client wraps the
     * transport failure in a bare {@link RuntimeException} — so it is
     * recognised by its cause rather than its type.
     *
     * <p>Matching on the cause instead of catching {@code RuntimeException}
     * wholesale is what stops a genuine bug here from being reported to
     * operators as an outage.
     */
    private static boolean isStorageFailure(RuntimeException e) {
        if (e instanceof AzureException || e instanceof UncheckedIOException) {
            return true;
        }
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return true;
            }
        }
        return false;
    }
}
