package dev.lavra.shared.web;

/**
 * Thrown when a resource does not exist <em>or</em> belongs to another user.
 *
 * <p>The two cases are deliberately indistinguishable: strict ownership answers
 * 404, never 403, so the API does not leak the existence of someone else's
 * resource (spec 0004).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
