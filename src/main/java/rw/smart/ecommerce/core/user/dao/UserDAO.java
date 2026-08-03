package rw.smart.ecommerce.core.user.dao;

import rw.smart.ecommerce.core.user.model.User;
import rw.smart.ecommerce.utils.DBConnection;

import java.sql.*;

public class UserDAO {

    public User findById(int userId) throws SQLException {
        String sql = "SELECT * FROM Users WHERE user_id = ?";
        try(Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try(ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /**
     * Used for login — hits the UNIQUE index on email.
     *
     */
    public User findByEmail(String email) throws SQLException {
        String sql = "SELECT * FROM Users WHERE email = ?";
        try(Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try(ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public int insert(User user) throws SQLException {
        String sql = "INSERT INTO Users (username, email, password_hash, full_name, phone) " +
                "VALUES (?, ?, ?, ?, ?) RETURNING user_id";
        try(Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getFullName());
            ps.setString(5, user.getPhone());

            try(ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }

        }
    }

    public boolean update(User user) throws SQLException {
        String sql = "UPDATE Users SET username = ?, email = ?, full_name = ?, phone = ? WHERE user_id = ?";
        try(Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getPhone());
            ps.setInt(5, user.getUserId());

            return ps.executeUpdate() > 0;
        }
    }


    public User mapRow(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new User(
                rs.getInt("user_id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("full_name"),
                rs.getString("phone"),
                createdAt != null ? createdAt.toLocalDateTime() :null
        );
    }
}
