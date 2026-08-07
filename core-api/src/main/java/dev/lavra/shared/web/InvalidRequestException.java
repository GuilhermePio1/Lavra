package dev.lavra.shared.web;

/**
 * Thrown when the request is malformed in a way bean validation cannot see —
 * a filename whose extension contradicts the declared content type, a blob that
 * is not where the client said it uploaded it.
 *
 * <p>Answers 400 with a code of its own rather than the generic
 * {@code VALIDATION_ERROR}, because the contract names these codes next to the
 * routes that return them and the frontend reacts differently to each: re-send
 * the file, or fix the form.
 */
public class InvalidRequestException extends RuntimeException {

    private final String code;

    public InvalidRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
