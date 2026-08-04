package dev.lavra.shared.blob;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param connectionString credentials for the storage account; the Azurite
 *                         well-known account locally, a Key Vault secret in Azure
 * @param container        container holding every episode artifact —
 *                         {@code raw/}, {@code processed/} are prefixes inside it
 */
@ConfigurationProperties("lavra.blob")
record BlobStorageProperties(String connectionString, String container) {
}
