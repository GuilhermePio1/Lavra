package dev.lavra.shared.web;

/**
 * Thrown when the request is legitimate but the plan in force does not allow it
 * — a show over {@code maxShows}, an upload with no minutes left (spec 0004).
 *
 * <p>Answers 403, not 404: unlike ownership, there is nothing to hide here. The
 * user owns the situation and needs to be told exactly what the limit was, so
 * the message is the one the frontend shows.
 */
public class PlanLimitExceededException extends RuntimeException {

    private final String code;

    public PlanLimitExceededException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
