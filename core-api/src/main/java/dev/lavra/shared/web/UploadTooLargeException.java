package dev.lavra.shared.web;

/**
 * Thrown when the declared upload is bigger than the API accepts.
 *
 * <p>A type of its own because the contract answers 413 here, and bean
 * validation could only produce a 400: the size limit is checked on the number
 * the client declares, long before any byte is written, so the refusal happens
 * while there is still nothing to reject.
 */
public class UploadTooLargeException extends RuntimeException {

    public UploadTooLargeException(String message) {
        super(message);
    }
}
