package dev.lavra.shared.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param connectionString namespace credentials; the local emulator by default,
 *                         a Key Vault secret in Azure
 */
@ConfigurationProperties("lavra.messaging")
record MessagingProperties(String connectionString) {
}
