package dev.lavra.show.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code ShowCreateRequest} of the REST contract. */
record ShowCreateRequest(@NotBlank @Size(max = 120) String name,
                         @Size(max = 500) String description) {
}
