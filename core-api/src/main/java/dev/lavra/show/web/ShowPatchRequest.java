package dev.lavra.show.web;

import jakarta.validation.constraints.Size;

/**
 * {@code ShowPatch} of the REST contract: every field optional, only the ones
 * sent are applied.
 *
 * <p>An omitted field and an explicit {@code null} are the same thing here —
 * both leave the current value alone. Clearing the description is done by
 * sending an empty string, which keeps the payload unambiguous without a
 * three-state wrapper around every field.
 */
record ShowPatchRequest(@Size(min = 1, max = 120) String name,
                        @Size(max = 500) String description) {
}
