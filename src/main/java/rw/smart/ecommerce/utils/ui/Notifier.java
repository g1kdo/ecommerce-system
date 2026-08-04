package rw.smart.ecommerce.utils.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;

/**
 * Toast/dialog helpers shared by every controller, so the notification look and
 * feel is defined once instead of being copy-pasted per screen.
 */
public final class Notifier {

    private Notifier() {
        // utility class, no instances
    }

    public static void error(String title, Throwable cause) {
        Notifications.create()
                .title(title)
                .text(cause.getMessage() == null ? cause.toString() : cause.getMessage())
                .position(Pos.BOTTOM_RIGHT)
                .hideAfter(Duration.seconds(4))
                .showError();
    }

    public static void info(String message) {
        Notifications.create()
                .title("Success")
                .text(message)
                .position(Pos.BOTTOM_RIGHT)
                .hideAfter(Duration.seconds(3))
                .showInformation();
    }

    public static void warn(String message) {
        Notifications.create()
                .title("Notice")
                .text(message)
                .position(Pos.BOTTOM_RIGHT)
                .hideAfter(Duration.seconds(3))
                .showWarning();
    }

    /** Blocking yes/no confirmation used before destructive actions. */
    public static boolean confirm(String header, String detail) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Please confirm");
        confirm.setHeaderText(header);
        confirm.setContentText(detail);
        return confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
}
