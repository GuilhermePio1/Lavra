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
}
