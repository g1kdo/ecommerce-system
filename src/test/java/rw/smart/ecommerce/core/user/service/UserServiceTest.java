package rw.smart.ecommerce.core.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rw.smart.ecommerce.core.user.dao.UserDAO;
import rw.smart.ecommerce.core.user.model.User;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Password hashing and authentication behaviour, with the DAO mocked out. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String PLAIN_PASSWORD = "secret123";

    @Mock
    private UserDAO userDAO;

    private UserService userService() {
        return new UserService(userDAO);
    }

    private User account() {
        User user = new User();
        user.setUsername("jdoe");
        user.setEmail("jdoe@example.com");
        user.setFullName("John Doe");
        return user;
    }

    @Test
    @DisplayName("register never persists the plaintext password")
    void registerHashesPassword() throws SQLException {
        when(userDAO.insert(any(User.class))).thenReturn(5);

        User user = account();
        assertEquals(5, userService().register(user, PLAIN_PASSWORD));

        ArgumentCaptor<User> persisted = ArgumentCaptor.forClass(User.class);
        verify(userDAO).insert(persisted.capture());
        String hash = persisted.getValue().getPasswordHash();

        assertNotEquals(PLAIN_PASSWORD, hash);
        assertTrue(hash.matches("[0-9a-f]{64}"), "expected a SHA-256 hex digest but got: " + hash);
    }

    @Test
    @DisplayName("hashing is stable, so a registered password authenticates later")
    void registeredPasswordAuthenticates() throws SQLException {
        when(userDAO.insert(any(User.class))).thenReturn(5);
        User user = account();
        userService().register(user, PLAIN_PASSWORD);

        when(userDAO.findByEmail("jdoe@example.com")).thenReturn(user);

        assertNotNull(userService().authenticate("jdoe@example.com", PLAIN_PASSWORD));
    }

    @Test
    void rejectsWrongPassword() throws SQLException {
        when(userDAO.insert(any(User.class))).thenReturn(5);
        User user = account();
        userService().register(user, PLAIN_PASSWORD);

        when(userDAO.findByEmail("jdoe@example.com")).thenReturn(user);

        assertNull(userService().authenticate("jdoe@example.com", "wrong-password"));
    }

    @Test
    void rejectsUnknownEmail() throws SQLException {
        when(userDAO.findByEmail("nobody@example.com")).thenReturn(null);

        assertNull(userService().authenticate("nobody@example.com", PLAIN_PASSWORD));
    }

    @Test
    void getUserAndUpdateProfileDelegateToTheDao() throws SQLException {
        User user = account();
        user.setUserId(5);
        when(userDAO.findById(5)).thenReturn(user);
        when(userDAO.update(user)).thenReturn(true);

        assertSame(user, userService().getUser(5));
        assertTrue(userService().updateProfile(user));
        verify(userDAO).update(user);
    }
}
