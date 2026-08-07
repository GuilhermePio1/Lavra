package dev.lavra.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Translates exceptions into the {@link ApiError} shape of the REST contract. */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(ApiError.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(PlanLimitExceededException.class)
    ResponseEntity<ApiError> handlePlanLimit(PlanLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(ResourceConflictException.class)
    ResponseEntity<ApiError> handleConflict(ResourceConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(UploadTooLargeException.class)
    ResponseEntity<ApiError> handleUploadTooLarge(UploadTooLargeException ex) {
        // 413, under the name RFC 9110 gave it.
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ApiError(ApiError.UPLOAD_TOO_LARGE, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                // A class-level constraint fails with no field error attached:
                // the request is invalid, but there is nothing to join.
                .body(new ApiError(ApiError.VALIDATION_ERROR,
                        message.isEmpty() ? "Invalid request" : message));
    }

    /**
     * Constraints on query parameters and path variables — a page size beyond
     * the maximum the contract allows, for instance. Spring validates those
     * separately from the request body, and without this the generic handler
     * below would turn a client mistake into a 500.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> handleParameterValidation(HandlerMethodValidationException ex) {
        String message = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName()
                                + ": " + error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ApiError(ApiError.VALIDATION_ERROR,
                        message.isEmpty() ? "Invalid request" : message));
    }

    /**
     * A parameter that cannot even be parsed into its type: an id that is not a
     * UUID, a status outside the enum. The request is malformed, not the
     * server's fault.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError(ApiError.VALIDATION_ERROR,
                        "%s: '%s' is not a valid value".formatted(ex.getName(), ex.getValue())));
    }

    /**
     * Last resort. The cause is logged in full but never returned — an
     * unexpected failure must not leak internals to the client.
     *
     * <p>Method and path are logged with it because a stack trace on its own
     * says what broke and not what it was working on; for these routes the path
     * carries the episode or show id. The query string is left out: it would add
     * nothing here and is the part likelier to hold something not worth writing
     * down.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(ApiError.INTERNAL_ERROR, "Unexpected error"));
    }
}
