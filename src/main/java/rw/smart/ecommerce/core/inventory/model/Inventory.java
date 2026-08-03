package rw.smart.ecommerce.core.inventory.model;

import java.time.LocalDateTime;

public class Inventory {

    private int inventoryId;
    private int productId;
    private int quantity;
    private LocalDateTime lastUpdated;

    public Inventory() {

    }

    public Inventory(int inventoryId, int productId, int quantity, LocalDateTime lastUpdated) {
        this.inventoryId = inventoryId;
        this.productId = productId;
        this.quantity = quantity;
        this.lastUpdated = lastUpdated;
    }

    public int getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(int inventoryId) {
        this.inventoryId = inventoryId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
