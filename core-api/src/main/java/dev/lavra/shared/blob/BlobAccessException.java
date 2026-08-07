package dev.lavra.shared.blob;

/**
 * A Blob Storage operation could not be completed: the account is unreachable,
 * the credential was refused, the service answered an error.
 *
 * <p>It exists so that nothing outside this package has to import
 * {@code com.azure} to reason about storage failing — the exception type is as
 * much a part of the port's contract as the method signatures are (ADR-0013,
 * ADR-0015).
 *
 * <p>Deliberately one type and not a hierarchy of transient and permanent
 * failures: nothing in the service branches on that difference, and a
 * classification nobody reads is a classification nobody keeps honest. The
 * originating SDK exception is always the cause, so the status code and the
 * detail survive into the log.
 */
public class BlobAccessException extends RuntimeException {

    public BlobAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
