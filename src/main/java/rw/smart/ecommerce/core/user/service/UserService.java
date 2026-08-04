package rw.smart.ecommerce.core.user.service;

import rw.smart.ecommerce.core.user.dao.UserDAO;
import rw.smart.ecommerce.core.user.model.User;
import rw.smart.ecommerce.utils.validation.RegexValidator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;

public class UserService {

    private final UserDAO userDAO = new UserDAO();

    public int register(User user, String plainPassword) throws SQLException {
        user.setPasswordHash(hash(plainPassword));
        return userDAO.insert(user);
    }

    public User authenticate(String email, String plainPassword) throws SQLException {
        User user = userDAO.findByEmail(email);
        if (user != null && user.getPasswordHash().equals(hash(plainPassword))) return user;

        return null;
    }

    public User getUser(int userId) throws SQLException {
        return userDAO.findById(userId);
    }

    public boolean updateProfile(User user) throws SQLException {
        return userDAO.update(user);
    }

    private String hash(String plainText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
