package dev.lavra.shared.messaging;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The schemas in {@code contracts/events/}, as the tests see them.
 *
 * <p>They are read from the contract directory itself (the build copies it onto
 * the test classpath), so there is no second copy to drift: editing a schema
 * changes what the tests accept, immediately.
 */
final class EventSchemas {

    private static final SchemaRegistry REGISTRY =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private static final Map<String, Schema> CACHE = new ConcurrentHashMap<>();

    private EventSchemas() {
    }

    /** Validation errors for this JSON against the schema of its event type. */
    static List<Error> validate(String eventType, String json) {
        return schemaOf(eventType).validate(json, InputFormat.JSON);
    }

    /** Fails loudly if the JSON does not satisfy the published contract. */
    static void assertValid(String eventType, String json) {
        List<Error> errors = validate(eventType, json);
        if (!errors.isEmpty()) {
            throw new AssertionError("Message does not satisfy %s.json:%n%s%n%nMessage was:%n%s"
                    .formatted(eventType, errors, json));
        }
    }

    private static Schema schemaOf(String eventType) {
        return CACHE.computeIfAbsent(eventType, type -> {
            String resource = "contracts/events/%s.json".formatted(type);
            try (InputStream schema = EventSchemas.class.getClassLoader().getResourceAsStream(resource)) {
                if (schema == null) {
                    throw new IllegalArgumentException(
                            "No schema on the classpath for " + type + " (expected " + resource + ")");
                }
                return REGISTRY.getSchema(schema);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not read " + resource, e);
            }
        });
    }
}
