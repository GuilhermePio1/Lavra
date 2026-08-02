package dev.lavra.shared.web;

/**
 * Error payload of the REST contract ({@code ApiError} in
 * {@code contracts/openapi/core-api.v1.yaml}).
 *
 * @param code    stable, machine-readable code the frontend branches on
 * @param message human-readable explanation
 */
public record ApiError(String code, String message) {

    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    // Codes the frontend branches on to explain a refusal, rather than just
    // reporting it. Each one is named in the contract next to the route that
    // returns it.
    public static final String PLAN_SHOW_LIMIT_EXCEEDED = "PLAN_SHOW_LIMIT_EXCEEDED";
    public static final String SHOW_NOT_EMPTY = "SHOW_NOT_EMPTY";
}
