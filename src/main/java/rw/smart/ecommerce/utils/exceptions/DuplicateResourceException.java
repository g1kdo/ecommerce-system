package rw.smart.ecommerce.utils.exceptions;

/**
 * A uniqueness rule (email, username, SKU, category name) was violated. Checked
 * in the service so the caller gets a readable 409 instead of a raw constraint
 * violation from the driver.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
