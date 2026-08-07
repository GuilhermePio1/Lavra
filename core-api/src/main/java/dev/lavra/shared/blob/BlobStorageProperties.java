package dev.lavra.shared.blob;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param connectionString    credentials for the storage account; the Azurite
 *                            well-known account locally, a Key Vault secret in Azure
 * @param container           container holding every episode artifact —
 *                            {@code raw/}, {@code processed/} are prefixes inside it
 * @param autoCreateContainer whether to create the container on first use. True
 *                            for the local stack, where Azurite starts empty;
 *                            false wherever infrastructure-as-code has already
 *                            created it, which also makes issuing a write
 *                            ticket an operation that touches no network at all
 */
@ConfigurationProperties("lavra.blob")
record BlobStorageProperties(String connectionString, String container, boolean autoCreateContainer) {
}
