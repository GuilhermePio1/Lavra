package dev.lavra.shared.blob;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Blob Storage over the Azure SDK, talking to Azurite locally and to a real
 * storage account in Azure — the same code either way, only the connection
 * string changes.
 */
class AzureBlobStorage implements BlobStorage {

    private static final int NOT_FOUND = 404;

    private final BlobContainerClient container;
    private final Clock clock;

    /**
     * The container is created on first use rather than at startup: connecting
     * to storage while the context builds would make every {@code @SpringBootTest}
     * in the codebase — most of which never touch a blob — require Azurite to be
     * running.
     */
    private volatile boolean containerEnsured;

    AzureBlobStorage(BlobContainerClient container, Clock clock) {
        this.container = container;
        this.clock = clock;
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
        try {
            BlobProperties properties = container.getBlobClient(blobPath).getProperties();
            return Optional.of(new BlobMetadata(blobPath, properties.getBlobSize(), properties.getContentType()));
        } catch (BlobStorageException e) {
            if (e.getStatusCode() == NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void delete(String blobPath) {
        container.getBlobClient(blobPath).deleteIfExists();
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
     */
    private String sign(BlobClient blob, BlobServiceSasSignatureValues values) {
        return blob.generateSas(values);
    }

    private void ensureContainer() {
        if (!containerEnsured) {
            container.createIfNotExists();
            containerEnsured = true;
        }
    }
}
