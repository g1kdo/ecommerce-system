package rw.smart.ecommerce.core.log.enums;

/**
 * Event types this application writes to the log collection. The stored document
 * keeps the raw {@code name()}; because the log store is schema-loose, readers do
 * not require the stored value to match this enum (documents written by other
 * tools or older builds still display).
 */
public enum EventType {
    LOGIN("Sign in"),
    LOGIN_FAILED("Failed sign in"),
    LOGOUT("Sign out"),
    REGISTER("Account created"),
    PRODUCT_SEARCH("Product search"),
    PRODUCT_CREATED("Product created"),
    PRODUCT_UPDATED("Product updated"),
    PRODUCT_DELETED("Product deleted"),
    CATEGORY_CREATED("Category created"),
    CATEGORY_UPDATED("Category updated"),
    CATEGORY_DELETED("Category deleted"),
    STOCK_UPDATED("Stock updated"),
    ORDER_PLACED("Order placed"),
    ORDER_REJECTED("Order rejected"),
    ORDER_STATUS_CHANGED("Order status changed"),
    REVIEW_SUBMITTED("Review submitted"),
    REVIEW_MARKED_HELPFUL("Review marked helpful"),
    PROFILE_UPDATED("Profile updated");

    private final String label;

    EventType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }

    /** Display label for a stored value, falling back to the raw string. */
    public static String labelOf(String storedName) {
        if (storedName == null) return "";

        for (EventType type : values()) {
            if (type.name().equals(storedName)) return type.label;
        }
        return storedName;
    }
}
