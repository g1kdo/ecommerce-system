package rw.smart.ecommerce.core.review.dao;

import rw.smart.ecommerce.core.review.model.Review;
import rw.smart.ecommerce.utils.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReviewDAO {

    public int insert(Review review) throws SQLException {
        String sql = "INSERT INTO Reviews (product_id, user_id, rating) VALUES (?, ?, ?) RETURNING review_id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, review.getProductId());
            ps.setInt(2, review.getUserId());
            ps.setInt(3, review.getRating());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Records a rating, replacing the author's previous one if present — the
     * UNIQUE (product_id, user_id) constraint allows only one rating per user
     * per product, so a re-rate is an update rather than a second row.
     */
    public int upsertRating(int productId, int userId, int rating) throws SQLException {
        String sql = "INSERT INTO Reviews (product_id, user_id, rating) VALUES (?, ?, ?) " +
                "ON CONFLICT (product_id, user_id) DO UPDATE " +
                "SET rating = EXCLUDED.rating, created_at = CURRENT_TIMESTAMP " +
                "RETURNING review_id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, userId);
            ps.setInt(3, rating);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public List<Review> findByProduct(int productId) throws SQLException {
        String sql = "SELECT * FROM Reviews WHERE product_id = ?";
        List<Review> results = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    results.add(new Review(
                            rs.getInt("review_id"),
                            rs.getInt("product_id"),
                            rs.getInt("user_id"),
                            rs.getInt("rating"),
                            createdAt != null ? createdAt.toLocalDateTime() : null
                    ));
                }
            }
        }
        return results;
    }

    public BigDecimal getAverageRating(int productId) throws SQLException {
        String sql = "SELECT AVG(rating) AS avg_rating FROM Reviews WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getBigDecimal("avg_rating") != null) {
                    return rs.getBigDecimal("avg_rating");
                }
                return BigDecimal.ZERO;
            }
        }
    }
}
