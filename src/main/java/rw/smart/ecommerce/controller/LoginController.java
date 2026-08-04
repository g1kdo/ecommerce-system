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
import rw.smart.ecommerce.core.log.enums.EventType;
import rw.smart.ecommerce.core.log.service.LogService;
import rw.smart.ecommerce.core.user.model.User;
import rw.smart.ecommerce.core.user.service.UserService;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;
import rw.smart.ecommerce.utils.session.Session;
import rw.smart.ecommerce.utils.ui.Navigation;
import rw.smart.ecommerce.utils.ui.Notifier;
import rw.smart.ecommerce.utils.validation.RegexValidator;

import java.sql.SQLException;
import java.util.Map;

/**
 * Entry screen. Authenticates against UserService (which compares the SHA-256
 * hash) and, on success, puts the user in the Session before opening the shell.
 */
public class LoginController {

    @FXML private StackPane rootPane;
    @FXML private VBox loginCard;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;
    @FXML private Button loginButton;
    @FXML private Button registerButton;

    private final UserService userService = new UserService();
    private final LogService logService = new LogService();

    @FXML
    public void initialize() {
        rootPane.getStyleClass().add("app-shell");
        loginCard.getStyleClass().add("card");

        loginButton.getStyleClass().addAll("btn", "btn-primary");
        loginButton.setGraphic(new FontIcon(FontAwesomeSolid.SIGN_IN_ALT));
        loginButton.setContentDisplay(ContentDisplay.LEFT);
        loginButton.setDefaultButton(true);
        registerButton.getStyleClass().addAll("btn", "btn-link");

        passwordField.setOnAction(event -> onLogin());
    }

    @FXML
    private void onLogin() {
        hideMessage();
        String email = text(emailField);
        String password = passwordField.getText();

        if (email.isBlank() || password == null || password.isBlank()) {
            showMessage("Enter both your email and password.");
            return;
        }

        try {
            RegexValidator.validateUserEmail(email);
            User user = userService.authenticate(email, password);
            if (user == null) {
                // no user id and no email in the log entry - just the outcome
                logService.log(EventType.LOGIN_FAILED, null, Map.of("reason", "invalid_credentials"));
                showMessage("Those credentials do not match any account.");
                return;
            }

            Session.login(user);
            logService.log(EventType.LOGIN, Map.of("username", user.getUsername()));
            Navigation.showMainShell(Navigation.stageOf(rootPane));
        } catch (InvalidInputException e) {
            showMessage(e.getMessage());
        } catch (SQLException e) {
            Notifier.error("Sign in failed", e);
        }
    }

    @FXML
    private void onGoToRegister() {
        Navigation.showRegister(Navigation.stageOf(rootPane));
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
