package dev.lavra.shared.web;

/**
 * Thrown when the resource exists and belongs to the caller, but its current
 * state refuses the operation — deleting a show that still has episodes,
 * exporting an episode that is not {@code READY}.
 *
 * <p>Answers 409: retrying is pointless until the state changes.
 */
public class ResourceConflictException extends RuntimeException {

    private final String code;

    public ResourceConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
