package dev.lavra.shared.blob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.core.exception.AzureException;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.policy.RequestRetryOptions;
import com.azure.storage.common.policy.RetryPolicyType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the port does when storage is not there. No Azurite and no Docker: the
 * client points at a port nothing is listening on, which is the cheapest
 * faithful stand-in for Azure being unreachable.
 *
 * <p>Two things are under test, and neither is the SDK. That a failure crosses
 * the port as a {@link BlobAccessException} rather than as an Azure type — the
 * port is worth nothing if callers have to know Azure to handle it. And that
 * issuing a write ticket succeeds anyway, because signing is local: the claim
 * in the port's Javadoc, held to by a test rather than by a comment.
 */
class AzureBlobStorageFailureTest {

    /**
     * Azurite's well-known development credentials, aimed at a dead port. The
     * key has to be real base64 for the SAS to be signable at all — which is
     * exactly what the "issuing works offline" test needs.
     */
    private static final String UNREACHABLE = "DefaultEndpointsProtocol=http;"
            + "AccountName=devstoreaccount1;"
            + "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
            + "BlobEndpoint=http://127.0.0.1:10999/devstoreaccount1;";

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC);
    private static final String PATH = "raw/9f1c5f2a-0000-4000-8000-000000000000/original.mp3";

    private static BlobContainerClient unreachableContainer() {
        return new BlobServiceClientBuilder()
                .connectionString(UNREACHABLE)
                .serviceVersion(BlobConfiguration.SERVICE_VERSION)
                // One attempt: the SDK's default would retry a connection that
                // is never going to be accepted, and the test would pay for it.
                .retryOptions(new RequestRetryOptions(RetryPolicyType.FIXED, 1, 2, null, null, null))
                .buildClient()
                .getBlobContainerClient("episodes");
    }

    private static AzureBlobStorage storage(boolean autoCreateContainer) {
        return new AzureBlobStorage(unreachableContainer(), CLOCK, autoCreateContainer);
    }

    @Test
    @DisplayName("describing against unreachable storage fails as BlobAccessException, not as an Azure type")
    void describeWrapsTheFailure() {
        assertThatThrownBy(() -> storage(false).describe(PATH))
                .isInstanceOf(BlobAccessException.class)
                .isNotInstanceOf(AzureException.class)
                .hasMessageContaining(PATH)
                // The original failure is kept underneath, so the detail still
                // reaches the log. It is also the shape that made the wrapping
                // subtle: the sync storage client reports an unreachable
                // account as a bare RuntimeException over an IOException, not
                // as any Azure type.
                .hasRootCauseInstanceOf(java.net.ConnectException.class);
    }

    @Test
    @DisplayName("deleting against unreachable storage fails as BlobAccessException")
    void deleteWrapsTheFailure() {
        assertThatThrownBy(() -> storage(false).delete(PATH))
                .isInstanceOf(BlobAccessException.class)
                .isNotInstanceOf(AzureException.class);
    }

    @Test
    @DisplayName("a write ticket is still issued with storage down, because signing never leaves the process")
    void issuesTicketWithoutTouchingStorage() {
        WriteTicket ticket = storage(false).issueWriteTicket(PATH, Duration.ofHours(2));

        assertThat(ticket.blobPath()).isEqualTo(PATH);
        assertThat(ticket.url().toString()).contains("sig=");
    }

    @Test
    @DisplayName("with container auto-creation on, issuing does touch storage — and fails when it is down")
    void autoCreatingTheContainerIsTheOneNetworkCall() {
        assertThatThrownBy(() -> storage(true).issueWriteTicket(PATH, Duration.ofHours(2)))
                .isInstanceOf(BlobAccessException.class)
                .isNotInstanceOf(AzureException.class)
                .hasMessageContaining("episodes");

        // Which is the whole argument for turning it off in Azure: the same
        // call, with the container already provisioned, cannot fail this way.
        assertThatCode(() -> storage(false).issueWriteTicket(PATH, Duration.ofHours(2)))
                .doesNotThrowAnyException();
    }
}
