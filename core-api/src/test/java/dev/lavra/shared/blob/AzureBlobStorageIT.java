package dev.lavra.shared.blob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.azure.AzuriteContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The Blob port against a real Azurite: what matters is not that the SDK works,
 * but that the credential this service hands to a browser does exactly what
 * ADR-0011 promises — write one blob, for a while, and nothing else.
 *
 * <p>No Spring context: the adapter takes a container client and a clock, so
 * there is nothing to wire.
 */
@Testcontainers
class AzureBlobStorageIT {

    @Container
    static final AzuriteContainer AZURITE = new AzuriteContainer("mcr.microsoft.com/azure-storage/azurite:3.36.0");

    private static final String CONTAINER = "episodes";
    private static final Duration TTL = Duration.ofHours(2);

    /**
     * Fixed, but fixed to the moment the suite starts: expiry assertions become
     * exact without the credentials being born already expired.
     */
    private static final Instant NOW = Instant.now();
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static AzureBlobStorage storage;

    @BeforeAll
    static void setUp() {
        storage = new AzureBlobStorage(containerClient(), CLOCK);
    }

    private static BlobContainerClient containerClient() {
        return new BlobServiceClientBuilder()
                .connectionString(AZURITE.getConnectionString())
                .serviceVersion(BlobConfiguration.SERVICE_VERSION)
                .buildClient()
                .getBlobContainerClient(CONTAINER);
    }

    /** A path shaped like the real thing, unique per test. */
    private static String somePath() {
        return "raw/" + UUID.randomUUID() + "/original.mp3";
    }

    /** The client a browser would build: it knows the URL and nothing else. */
    private static BlobClient clientFor(String urlWithCredential) {
        return new BlobClientBuilder()
                .endpoint(urlWithCredential)
                .serviceVersion(BlobConfiguration.SERVICE_VERSION)
                .buildClient();
    }

    private static void upload(BlobClient client, byte[] content, String contentType) {
        client.uploadWithResponse(
                new BlobParallelUploadOptions(BinaryData.fromBytes(content))
                        .setHeaders(new BlobHttpHeaders().setContentType(contentType)),
                null, null);
    }

    @Test
    @DisplayName("the ticket uploads the blob, and the metadata comes back as declared")
    void uploadsWithTheTicketAndReadsMetadataBack() {
        String path = somePath();
        byte[] content = "not really an mp3".getBytes();

        WriteTicket ticket = storage.issueWriteTicket(path, TTL);
        assertThat(ticket.blobPath()).isEqualTo(path);
        assertThat(ticket.expiresAt()).isEqualTo(NOW.plus(TTL));

        assertThat(storage.describe(path)).isEmpty();

        upload(clientFor(ticket.url().toString()), content, "audio/mpeg");

        assertThat(storage.describe(path)).hasValueSatisfying(metadata -> {
            assertThat(metadata.blobPath()).isEqualTo(path);
            assertThat(metadata.sizeBytes()).isEqualTo(content.length);
            assertThat(metadata.contentType()).isEqualTo("audio/mpeg");
        });
    }

    @Test
    @DisplayName("the ticket cannot read back what it wrote")
    void ticketGrantsNoRead() {
        String path = somePath();
        WriteTicket ticket = storage.issueWriteTicket(path, TTL);
        BlobClient asBrowser = clientFor(ticket.url().toString());

        upload(asBrowser, "audio".getBytes(), "audio/mpeg");

        assertThatThrownBy(asBrowser::downloadContent)
                .isInstanceOf(BlobStorageException.class)
                .satisfies(e -> assertThat(((BlobStorageException) e).getStatusCode()).isEqualTo(403));
    }

    @Test
    @DisplayName("the ticket is bound to its own blob: another path is refused")
    void ticketIsBoundToOneBlob() {
        WriteTicket ticket = storage.issueWriteTicket(somePath(), TTL);

        // Same credential, different blob — the shape an attacker with a leaked
        // ticket would try.
        String credential = ticket.url().toString().substring(ticket.url().toString().indexOf('?'));
        String otherBlobUrl = ticket.url().toString().replace("original.mp3", "somebody-elses.mp3");
        String smuggled = otherBlobUrl.substring(0, otherBlobUrl.indexOf('?')) + credential;

        assertThatThrownBy(() -> upload(clientFor(smuggled), "payload".getBytes(), "audio/mpeg"))
                .isInstanceOf(BlobStorageException.class)
                .satisfies(e -> assertThat(((BlobStorageException) e).getStatusCode()).isEqualTo(403));
    }

    @Test
    @DisplayName("an expired ticket is refused, so the TTL is not decorative")
    void expiredTicketIsRefused() {
        Clock threeHoursAgo = Clock.fixed(NOW.minus(Duration.ofHours(3)), ZoneOffset.UTC);
        AzureBlobStorage inThePast = new AzureBlobStorage(containerClient(), threeHoursAgo);

        WriteTicket ticket = inThePast.issueWriteTicket(somePath(), TTL);
        assertThat(ticket.expiresAt()).isBefore(NOW);

        assertThatThrownBy(() -> upload(clientFor(ticket.url().toString()), "late".getBytes(), "audio/mpeg"))
                .isInstanceOf(BlobStorageException.class)
                .satisfies(e -> assertThat(((BlobStorageException) e).getStatusCode()).isEqualTo(403));
    }

    @Test
    @DisplayName("describing a blob that was never uploaded is empty, not an error")
    void describeIsEmptyForMissingBlob() {
        assertThat(storage.describe(somePath())).isEmpty();
    }

    @Test
    @DisplayName("delete removes the blob and tolerates one that is already gone")
    void deletesAndToleratesMissing() {
        String path = somePath();
        WriteTicket ticket = storage.issueWriteTicket(path, TTL);
        upload(clientFor(ticket.url().toString()), "abandoned".getBytes(), "audio/mpeg");
        assertThat(storage.describe(path)).isPresent();

        storage.delete(path);
        assertThat(storage.describe(path)).isEmpty();

        // The cleanup job runs against upload attempts that may never have
        // produced a blob at all.
        storage.delete(path);
    }
}
