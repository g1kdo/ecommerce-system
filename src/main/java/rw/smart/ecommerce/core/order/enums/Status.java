package rw.smart.ecommerce.core.order.enums;

/**
 * Order lifecycle states. These must stay in sync with the CHECK constraint on
 * Orders.status in sql/schema.sql — persisting a value the constraint does not
 * allow fails at the database level.
 */
public enum Status {
    PENDING("Pending"),
    PAID("Paid"),
    SHIPPED("Shipped"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled");

    private final String label;

    Status(String label) {
        this.label = label;
    }

    /** Human-readable form used in the UI; {@code name()} is what is persisted. */
    @Override
    public String toString() {
        return label;
    }
}
