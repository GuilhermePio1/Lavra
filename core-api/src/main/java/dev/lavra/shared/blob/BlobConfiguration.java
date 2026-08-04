package dev.lavra.shared.blob;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.BlobServiceVersion;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Blob port. Building the clients is pure local computation — no
 * network call happens until a blob is actually touched — so a context that
 * never uploads anything starts fine without storage.
 */
@Configuration
@EnableConfigurationProperties(BlobStorageProperties.class)
class BlobConfiguration {

    /**
     * Pinned rather than left at the SDK's default, which is newer than any
     * released Azurite understands — an unpinned client answers every local
     * request with {@code InvalidHeaderValue}. The version also travels in the
     * SAS, so leaving it floating would break the uploads too.
     *
     * <p>Raise it when Azurite catches up, deliberately and in one place.
     */
    static final BlobServiceVersion SERVICE_VERSION = BlobServiceVersion.V2025_11_05;

    @Bean
    BlobContainerClient blobContainerClient(BlobStorageProperties properties) {
        return new BlobServiceClientBuilder()
                .connectionString(properties.connectionString())
                .serviceVersion(SERVICE_VERSION)
                .buildClient()
                .getBlobContainerClient(properties.container());
    }

    @Bean
    BlobStorage blobStorage(BlobContainerClient container, Clock clock) {
        return new AzureBlobStorage(container, clock);
    }
}
