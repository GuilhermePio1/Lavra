package dev.lavra.episode.domain;

/**
 * Raised when the client's declaration of the upload cannot be accepted.
 *
 * <p>Carries the {@link UploadDeclaration.Rejection} rather than an HTTP status:
 * the domain knows why it refused, the web layer knows what that is worth as a
 * status code — a size over the limit is a 413 and everything else is a 400,
 * per the contract.
 */
public class InvalidUploadDeclarationException extends RuntimeException {

    private final UploadDeclaration.Rejection rejection;

    public InvalidUploadDeclarationException(UploadDeclaration.Rejection rejection, String message) {
        super(message);
        this.rejection = rejection;
    }

    public UploadDeclaration.Rejection rejection() {
        return rejection;
    }
}
