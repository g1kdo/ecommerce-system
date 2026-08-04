package rw.smart.ecommerce.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import rw.smart.ecommerce.core.user.model.User;
import rw.smart.ecommerce.core.user.service.UserService;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;
import rw.smart.ecommerce.utils.session.Session;
import rw.smart.ecommerce.utils.ui.Navigation;
import rw.smart.ecommerce.utils.ui.Notifier;
import rw.smart.ecommerce.utils.validation.RegexValidator;

import java.sql.SQLException;

/**
 * Sign-up screen. Delegates hashing and persistence to UserService and signs the
 * new account straight in, so registration ends on the shop screen.
 */
public class RegisterController {

    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final String UNIQUE_VIOLATION = "23505";

    @FXML private StackPane rootPane;
    @FXML private VBox registerCard;
    @FXML private TextField fullNameField;
    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label messageLabel;
    @FXML private Button registerButton;
    @FXML private Button backToLoginButton;

    private final UserService userService = new UserService();

    @FXML
    public void initialize() {
        rootPane.getStyleClass().add("app-shell");
        registerCard.getStyleClass().add("card");

        registerButton.getStyleClass().addAll("btn", "btn-success");
        registerButton.setGraphic(new FontIcon(FontAwesomeSolid.USER_PLUS));
        registerButton.setContentDisplay(ContentDisplay.LEFT);
        registerButton.setDefaultButton(true);
        backToLoginButton.getStyleClass().addAll("btn", "btn-link");

        confirmPasswordField.setOnAction(event -> onRegister());
    }

    @FXML
    private void onRegister() {
        hideMessage();
        try {
            User user = buildValidatedUser();
            int userId = userService.register(user, passwordField.getText());
            user.setUserId(userId);

            Session.login(user);
            Notifier.info("Welcome, " + user.getFullName() + "!");
            Navigation.showMainShell(Navigation.stageOf(rootPane));
        } catch (InvalidInputException | IllegalArgumentException e) {
            showMessage(e.getMessage());
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                showMessage("That username or email is already registered.");
            } else {
                Notifier.error("Registration failed", e);
            }
        }
    }

    @FXML
    private void onBackToLogin() {
        Navigation.showLogin(Navigation.stageOf(rootPane));
    }

    private User buildValidatedUser() {
        String fullName = text(fullNameField);
        String username = text(usernameField);
        String email = text(emailField);
        String phone = text(phoneField);
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        String confirmation = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        if (fullName.isBlank()) throw new IllegalArgumentException("Full name is required.");
        if (username.isBlank()) throw new IllegalArgumentException("Username is required.");
        if (password.length() < MIN_PASSWORD_LENGTH)
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        if (!password.equals(confirmation)) throw new IllegalArgumentException("The two passwords do not match.");

        RegexValidator.validateUserEmail(email);

        User user = new User();
        user.setFullName(fullName);
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(phone.isBlank() ? null : phone);
        return user;
    }

    private String text(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private void showMessage(String message) {
        messageLabel.setText(message);
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    private void hideMessage() {
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);
    }
}
