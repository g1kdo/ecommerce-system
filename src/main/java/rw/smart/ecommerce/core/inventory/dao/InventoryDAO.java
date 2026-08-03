package rw.smart.ecommerce.core.inventory.dao;

import rw.smart.ecommerce.core.inventory.model.Inventory;
import rw.smart.ecommerce.utils.DBConnection;

import java.sql.*;

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
