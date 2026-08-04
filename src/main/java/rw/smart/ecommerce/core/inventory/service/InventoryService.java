package rw.smart.ecommerce.core.inventory.service;

import rw.smart.ecommerce.core.inventory.dao.InventoryDAO;
import rw.smart.ecommerce.core.inventory.model.Inventory;
import rw.smart.ecommerce.utils.exceptions.InsufficientStockException;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business logic layer for stock levels. Wraps InventoryDAO so the stock screen
 * and the shop screen never touch JDBC directly.
 */
public class InventoryService {

    private final InventoryDAO inventoryDAO = new InventoryDAO();

    public Inventory getInventory(int productId) throws SQLException {
        return inventoryDAO.findByProductId(productId);
    }

    /** Stock on hand, treating "no inventory row yet" as zero. */
    public int getStock(int productId) throws SQLException {
        Inventory inventory = inventoryDAO.findByProductId(productId);
        return inventory == null ? 0 : inventory.getQuantity();
    }

    /**
     * Stock for every product keyed by product_id — one query for a whole table
     * of products rather than one query per row.
     */
    public Map<Integer, Inventory> getStockByProduct() throws SQLException {
        List<Inventory> rows = inventoryDAO.findAll();
        Map<Integer, Inventory> byProduct = new HashMap<>();
        for (Inventory row : rows) {
            byProduct.put(row.getProductId(), row);
        }
        return byProduct;
    }

    public boolean setStock(int productId, int quantity) throws SQLException {
        if (quantity < 0) throw new InvalidInputException("Stock quantity cannot be negative.");

        return inventoryDAO.upsertQuantity(productId, quantity);
    }

    /**
     * Standalone stock decrement (restocking corrections, manual adjustments).
     * Order checkout does not use this — it decrements inside the order
     * transaction in {@code OrderService.placeOrder}.
     */
    public void reduceStock(int productId, int amount) throws SQLException {
        if (amount <= 0) throw new InvalidInputException("Amount to remove must be greater than zero.");

        boolean ok = inventoryDAO.decrementQuantity(productId, amount);
        if (!ok) throw new InsufficientStockException("Insufficient stock for product_id= " + productId);
    }
}
