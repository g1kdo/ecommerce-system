package rw.smart.ecommerce.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import rw.smart.ecommerce.core.user.model.User;
import rw.smart.ecommerce.core.user.service.UserService;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;
import rw.smart.ecommerce.utils.session.Session;
import rw.smart.ecommerce.utils.ui.Notifier;
import rw.smart.ecommerce.utils.ui.RefreshableView;
import rw.smart.ecommerce.utils.validation.RegexValidator;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;

/**
 * Account screen backed by UserService.getUser / updateProfile. Passwords are not
 * editable here — UserService only hashes on registration.
 */
public class ProfileController implements RefreshableView {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    @FXML private BorderPane rootPane;
    @FXML private VBox profileCard;
    @FXML private TextField fullNameField;
    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private Label memberSinceLabel;
    @FXML private Button saveButton;
    @FXML private Button resetButton;

    private final UserService userService = new UserService();

    @FXML
    public void initialize() {
        rootPane.getStyleClass().add("content-pane");
        profileCard.getStyleClass().add("card");

        saveButton.getStyleClass().addAll("btn", "btn-success");
        saveButton.setGraphic(new FontIcon(FontAwesomeSolid.SAVE));
        saveButton.setContentDisplay(ContentDisplay.LEFT);
        resetButton.getStyleClass().addAll("btn", "btn-default");
        resetButton.setGraphic(new FontIcon(FontAwesomeSolid.SYNC_ALT));
        resetButton.setContentDisplay(ContentDisplay.LEFT);

        populate(Session.currentUser());
    }

    private void populate(User user) {
        fullNameField.setText(user.getFullName());
        usernameField.setText(user.getUsername());
        emailField.setText(user.getEmail());
        phoneField.setText(user.getPhone());
        memberSinceLabel.setText(user.getCreatedAt() == null
                ? "" : "Member since " + user.getCreatedAt().format(DATE_FORMAT));
    }

    /** Re-entering the screen shows the session account, discarding stale edits. */
    @Override
    public void onShown() {
        populate(Session.currentUser());
    }

    @FXML
    private void onSave() {
        User current = Session.currentUser();
        try {
            User edited = buildValidatedUser(current.getUserId());
            if (!userService.updateProfile(edited)) {
                Notifier.warn("Your account could no longer be found.");
                return;
            }

            // keep the in-memory session in step with what was persisted
            current.setFullName(edited.getFullName());
            current.setUsername(edited.getUsername());
            current.setEmail(edited.getEmail());
            current.setPhone(edited.getPhone());
            Notifier.info("Profile updated successfully.");
        } catch (InvalidInputException | IllegalArgumentException e) {
            Notifier.warn(e.getMessage());
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                Notifier.warn("That username or email is already taken.");
            } else {
                Notifier.error("Profile update failed", e);
            }
        }
    }

    /** Discards edits by reloading the stored account. */
    @FXML
    private void onReset() {
        try {
            User stored = userService.getUser(Session.currentUserId());
            if (stored == null) {
                Notifier.warn("Your account could no longer be found.");
                return;
            }
            populate(stored);
        } catch (SQLException e) {
            Notifier.error("Failed to reload your profile", e);
        }
    }

    private User buildValidatedUser(int userId) {
        String fullName = text(fullNameField);
        String username = text(usernameField);
        String email = text(emailField);
        String phone = text(phoneField);

        if (fullName.isBlank()) throw new IllegalArgumentException("Full name is required.");
        if (username.isBlank()) throw new IllegalArgumentException("Username is required.");

        RegexValidator.validateUserEmail(email);

        User user = new User();
        user.setUserId(userId);
        user.setFullName(fullName);
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(phone.isBlank() ? null : phone);
        return user;
    }

    private String text(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }
}
