package rw.smart.ecommerce.utils.session;

import rw.smart.ecommerce.core.user.model.User;

/**
 * Holds the currently authenticated user for the lifetime of the application
 * process. Screens that need an owning user (orders, reviews, profile) read it
 * from here instead of passing a user id through every controller.
 */
public final class Session {

    private static User currentUser;

    private Session() {
        // utility class, no instances
    }

    public static void login(User user) {
        currentUser = user;
    }

    public static void logout() {
        currentUser = null;
    }

    public static boolean isAuthenticated() {
        return currentUser != null;
    }

    public static User currentUser() {
        if (currentUser == null) throw new IllegalStateException("No authenticated user in session");

        return currentUser;
    }

    public static int currentUserId() {
        return currentUser().getUserId();
    }
}
