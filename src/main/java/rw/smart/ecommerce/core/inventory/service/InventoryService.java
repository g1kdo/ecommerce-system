package rw.smart.ecommerce.core.inventory.service;

public interface InventoryService {

    /** Stock on hand, treating a missing inventory row as zero. */
    int getStock(Long productId);

    /** Absolute set, used by the administrator's stock screen. */
    int setStock(Long productId, int quantity);

    /**
     * Standalone decrement for corrections. Checkout does not use this — it
     * decrements inside the order transaction so the two cannot diverge.
     */
    void reduceStock(Long productId, int amount);
}
