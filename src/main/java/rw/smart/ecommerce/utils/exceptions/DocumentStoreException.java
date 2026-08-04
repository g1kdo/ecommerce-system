package rw.smart.ecommerce.utils.exceptions;

/**
 * Raised when the relational write of an operation succeeded but its document-store
 * half did not — for example a rating that was recorded in SQL while the review
 * body could not be written to the document store. Callers use this to tell the
 * user exactly what was and was not saved, instead of implying total failure.
 */
public class DocumentStoreException extends RuntimeException {

    public DocumentStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
