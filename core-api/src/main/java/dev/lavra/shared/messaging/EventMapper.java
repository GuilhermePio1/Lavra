package dev.lavra.shared.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON of the events, deliberately not the JSON of the REST API.
 *
 * <p>A published schema is immutable, so the wire format of a message must not
 * be able to change as a side effect of someone tuning the API's serialisation.
 * Sharing the auto-configured web {@code ObjectMapper} would allow exactly that;
 * this mapper is built here and configured here.
 */
final class EventMapper {

    private EventMapper() {
    }

    static ObjectMapper create() {
        return JsonMapper.builder()
                // Timestamps as ISO-8601 strings: the schemas say date-time.
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                // The schemas are additionalProperties:false with optional keys
                // meaning "use the default". A null must therefore be an absent
                // key, not a key holding null.
                .changeDefaultPropertyInclusion(value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
    }
}
