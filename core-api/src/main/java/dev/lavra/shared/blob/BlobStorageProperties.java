package dev.lavra.shared.blob;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validated so that a value that cannot possibly work stops the service at
 * startup rather than at the first upload of a real user.
 *
 * <p>Note what this does and does not buy. Every property here has a default in
 * {@code application.yml}, so "missing" is not a state that can be reached —
 * {@link NotBlank} catches the narrower case of an environment variable that is
 * set but empty, which is what a broken secret reference in a deployment
 * pipeline produces. Catching a value that is present and wrong is a different
 * job, and belongs to {@code DevelopmentDefaultsGuard}.
 *
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
@Validated
record BlobStorageProperties(

        @NotBlank
        String connectionString,

        /*
         * Azure's own rule for a container name: 3 to 63 characters, lowercase
         * letters, digits and hyphens, starting and ending on a letter or digit,
         * with no two hyphens in a row. Spelling it out here turns a typo into a
         * refusal to start; without it the same typo starts cleanly and fails
         * with InvalidResourceName the first time somebody uploads.
         */
        @NotBlank
        @Pattern(regexp = "[a-z0-9](?:[a-z0-9]|-(?=[a-z0-9])){2,62}",
                message = "must be a valid Azure container name: 3-63 lowercase letters, "
                        + "digits or hyphens, starting and ending alphanumeric")
        String container,

        boolean autoCreateContainer) {
}
