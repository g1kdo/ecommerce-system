package rw.smart.ecommerce.core.order.dao;

import rw.smart.ecommerce.core.order.enums.Status;
import rw.smart.ecommerce.core.order.model.Order;
import rw.smart.ecommerce.core.order.model.item.OrderItem;
import rw.smart.ecommerce.utils.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderDAO {

    /**
     * Inserts an order and all its items in a single JDBC transaction.
     * Either the whole order (header + lines) is committed, or none of it is.
     */
    public int insertOrderWithItems(Order order) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int orderId = insertOrderWithItems(conn, order);
                conn.commit();
                return orderId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public int insertOrderWithItems(Connection conn, Order order) throws SQLException {
        String orderSql = "INSERT INTO Orders (user_id, status, total_amount) VALUES (?, ?, ?) RETURNING order_id";
        String itemSql  = "INSERT INTO OrderItems (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)";

        int orderId;
        try (PreparedStatement ps = conn.prepareStatement(orderSql)) {
            ps.setInt(1, order.getUserId());
            ps.setString(2, order.getStatus().name());
            ps.setBigDecimal(3, order.getTotalAmount());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                orderId = rs.getInt(1);
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
            for (OrderItem item : order.getItems()) {
                ps.setInt(1, orderId);
                ps.setInt(2, item.getProductId());
                ps.setInt(3, item.getQuantity());
                ps.setBigDecimal(4, item.getUnitPrice());
                ps.addBatch();
            }
            ps.executeBatch();
        }

        return orderId;
    }

    public List<Order> findByUser(int userId) throws SQLException {
        String sql = "SELECT * FROM Orders WHERE user_id = ? ORDER BY order_date DESC";
        List<Order> results = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        }
        return results;
    }

    public List<OrderItem> findItemsByOrder(int orderId) throws SQLException {
        String sql = "SELECT * FROM OrderItems WHERE order_id = ?";
        List<OrderItem> results = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new OrderItem(
                            rs.getInt("order_item_id"),
                            rs.getInt("order_id"),
                            rs.getInt("product_id"),
                            rs.getInt("quantity"),
                            rs.getBigDecimal("unit_price")
                    ));
                }
            }
        }
        return results;
    }

    public boolean updateStatus(int orderId, Status status) throws SQLException {
        String sql = "UPDATE Orders SET status = ? WHERE order_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, orderId);
            return ps.executeUpdate() > 0;
        }
    }

    private Order mapRow(ResultSet rs) throws SQLException {
        Timestamp orderDate = rs.getTimestamp("order_date");
        return new Order(
                rs.getInt("order_id"),
                rs.getInt("user_id"),
                orderDate != null ? orderDate.toLocalDateTime() : null,
                Status.valueOf(rs.getString("status")),
                rs.getBigDecimal("total_amount")
        );
    }
}
