package dev.lavra.shared.messaging;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validated for the same reason as {@code BlobStorageProperties}: an empty value
 * — the shape a broken secret reference takes — should stop the service at
 * startup, not at the first event the pipeline tries to publish.
 *
 * <p>There is no counterpart to the container-name pattern here. Queue names
 * are not configuration: they live in {@link EventType}, next to the schema of
 * the event that travels on them.
 *
 * @param connectionString namespace credentials; the local emulator by default,
 *                         a Key Vault secret in Azure
 */
@ConfigurationProperties("lavra.messaging")
@Validated
record MessagingProperties(@NotBlank String connectionString) {
}
