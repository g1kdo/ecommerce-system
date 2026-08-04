package rw.smart.ecommerce.core.inventory.dao;

import rw.smart.ecommerce.core.inventory.model.Inventory;
import rw.smart.ecommerce.utils.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InventoryDAO {

    public Inventory findByProductId(int productId) throws SQLException {
        String sql = "SELECT * FROM Inventory WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /**
     * All stock rows in one round trip — the stock screen needs a quantity per
     * product and would otherwise issue one query per table row (N+1).
     */
    public List<Inventory> findAll() throws SQLException {
        String sql = "SELECT * FROM Inventory";
        List<Inventory> results = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    /**
     * Decrements stock after a sale. Throws if not enough stock (checked at Service level).
     *
     */
    public boolean decrementQuantity(int productId, int amount) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return decrementQuantity(conn, productId, amount);
        }
    }

    public boolean decrementQuantity(Connection conn, int productId, int amount) throws SQLException {
        String sql = "UPDATE Inventory SET quantity = quantity - ?, last_updated = CURRENT_TIMESTAMP " +
                "WHERE product_id = ? AND quantity >= ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, amount);
            ps.setInt(2, productId);
            ps.setInt(3, amount);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean setQuantity(int productId, int quantity) throws SQLException {
        String sql = "UPDATE Inventory SET quantity = ?, last_updated = CURRENT_TIMESTAMP WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setInt(2, productId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Sets stock for a product whether or not it already has an Inventory row —
     * products created through the product form start out without one.
     */
    public boolean upsertQuantity(int productId, int quantity) throws SQLException {
        String sql = "INSERT INTO Inventory (product_id, quantity) VALUES (?, ?) " +
                "ON CONFLICT (product_id) DO UPDATE " +
                "SET quantity = EXCLUDED.quantity, last_updated = CURRENT_TIMESTAMP";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, quantity);
            return ps.executeUpdate() > 0;
        }
    }

    private Inventory mapRow(ResultSet rs) throws SQLException {
        Timestamp lastUpdated = rs.getTimestamp("last_updated");
        return new Inventory(
                rs.getInt("inventory_id"),
                rs.getInt("product_id"),
                rs.getInt("quantity"),
                lastUpdated != null ? lastUpdated.toLocalDateTime() : null
        );
    }
}
