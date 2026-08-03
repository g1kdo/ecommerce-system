package rw.smart.ecommerce.core.product.dao;

import rw.smart.ecommerce.core.product.model.Product;
import rw.smart.ecommerce.utils.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Products.
 * All queries use PreparedStatement with bound parameters — no string
 * concatenation of user input anywhere in this class (SQL-injection safe).
 */
public class ProductDAO {

    public Product findById(int productId) throws SQLException {
        String sql = "SELECT * FROM Products WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<Product> findAll() throws SQLException {
        String sql = "SELECT * FROM Products ORDER BY name";
        List<Product> results = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    public List<Product> findByCategory(int categoryId, int limit, int offset) throws SQLException {
        String sql = "SELECT * FROM Products WHERE category_id = ? ORDER BY name LIMIT ? OFFSET ?";
        List<Product> results = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        }
        return results;
    }

    /** Case-insensitive search using the LOWER(name) functional index. */
    public List<Product> searchByName(String term) throws SQLException {
        String sql = "SELECT * FROM Products WHERE LOWER(name) LIKE ?";
        List<Product> results = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + term.toLowerCase() + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        }
        return results;
    }

    public int insert(Product product) throws SQLException {
        String sql = "INSERT INTO Products (name, description, price, sku, category_id) " +
                "VALUES (?, ?, ?, ?, ?) RETURNING product_id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, product.getName());
            ps.setString(2, product.getDescription());
            ps.setBigDecimal(3, product.getPrice());
            ps.setString(4, product.getSku());
            ps.setInt(5, product.getCategoryId());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public boolean update(Product product) throws SQLException {
        String sql = "UPDATE Products SET name = ?, description = ?, price = ?, " +
                "sku = ?, category_id = ? WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, product.getName());
            ps.setString(2, product.getDescription());
            ps.setBigDecimal(3, product.getPrice());
            ps.setString(4, product.getSku());
            ps.setInt(5, product.getCategoryId());
            ps.setInt(6, product.getProductId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int productId) throws SQLException {
        String sql = "DELETE FROM Products WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            return ps.executeUpdate() > 0;
        }
    }

    private Product mapRow(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new Product(
                rs.getInt("product_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                rs.getString("sku"),
                rs.getInt("category_id"),
                createdAt != null ? createdAt.toLocalDateTime() : null
        );
    }
}
