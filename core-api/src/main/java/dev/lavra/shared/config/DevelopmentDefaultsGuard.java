package dev.lavra.shared.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start when the service is configured for a real environment but is
 * still pointing at the local development stack.
 *
 * <p>{@code application.yml} gives every infrastructure property a default that
 * matches {@code infra/docker-compose.dev.yml}, so the service runs locally with
 * no setup. The cost of that convenience is a silent failure mode: an
 * environment variable misspelled in the Container App is not an error, it is an
 * absent override, and the service starts perfectly healthy pointing at
 * {@code 127.0.0.1}. Nothing complains until a real user uploads audio.
 *
 * <p>Bean validation cannot catch it — the value is not missing or malformed,
 * it is present and wrong for where it is running. What is needed is a second
 * opinion about which environment this is, and the OIDC issuer is the one the
 * project already uses for that: ADR-0012 makes it the single thing that
 * changes between local and Azure, with the local one being the emulator on
 * localhost. So: a non-local issuer means a real environment, and a real
 * environment may not use development infrastructure.
 *
 * <p>The two halves cover each other. Forget only the storage variable and the
 * issuer is still the real one, so this fires. Forget every variable and the
 * issuer is the emulator's, which this lets pass — but then the resource server
 * cannot fetch its metadata from a localhost that is not there, and startup
 * fails on that instead. Either way the service does not come up wrong.
 *
 * <p>There is deliberately no flag to switch this off. It would only be needed
 * to run against real Entra with local storage, which ADR-0012 does not
 * describe, and a guard against forgetting is worth little if forgetting can be
 * declared acceptable. Should that setup ever be wanted, add the escape hatch
 * knowingly rather than widening the rule.
 */
@Component
class DevelopmentDefaultsGuard {

    private static final String ISSUER_URI = "spring.security.oauth2.resourceserver.jwt.issuer-uri";
    private static final String BLOB_CONNECTION_STRING = "lavra.blob.connection-string";
    private static final String MESSAGING_CONNECTION_STRING = "lavra.messaging.connection-string";

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    DevelopmentDefaultsGuard(Environment environment) {
        rejectionReason(
                environment.getProperty(ISSUER_URI, ""),
                environment.getProperty(BLOB_CONNECTION_STRING, ""),
                environment.getProperty(MESSAGING_CONNECTION_STRING, ""))
                .ifPresent(reason -> {
                    throw new IllegalStateException(reason);
                });
    }

    /**
     * The whole rule, as a function of three strings, so that every combination
     * can be tested without a Spring context.
     *
     * @return why the configuration must be refused, or empty if it is coherent
     */
    static Optional<String> rejectionReason(String issuerUri, String blobConnectionString,
                                            String messagingConnectionString) {
        if (isLocal(issuerUri)) {
            return Optional.empty();
        }

        List<String> offenders = new ArrayList<>();
        // Azurite is reachable under either spelling: the shorthand, or the
        // well-known development account written out in full.
        if (containsIgnoringCase(blobConnectionString, "UseDevelopmentStorage=true")
                || containsIgnoringCase(blobConnectionString, "devstoreaccount1")) {
            offenders.add(BLOB_CONNECTION_STRING + " points at Azurite (set LAVRA_BLOB_CONNECTION_STRING)");
        }
        if (containsIgnoringCase(messagingConnectionString, "UseDevelopmentEmulator=true")) {
            offenders.add(MESSAGING_CONNECTION_STRING + " points at the Service Bus emulator "
                    + "(set LAVRA_SERVICEBUS_CONNECTION_STRING)");
        }
        if (offenders.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of("Refusing to start: the OIDC issuer '" + issuerUri + "' is not a local one, "
                + "so this is not the development stack, but " + String.join("; and ", offenders)
                + ". These are the local defaults from application.yml — the environment did not override them.");
    }

    /**
     * A blank or unparseable issuer counts as not local. It is a broken
     * configuration either way, and refusing is the safer reading of it.
     */
    private static boolean isLocal(String issuerUri) {
        try {
            String host = URI.create(issuerUri).getHost();
            return host != null && LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean containsIgnoringCase(String value, String token) {
        return value.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }
}
