package rw.smart.ecommerce.utils.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.bootstrapfx.BootstrapFX;

import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;

/**
 * Loads FXML views from a single well-known location and applies the shared
 * stylesheets to every scene it creates (including modal dialogs, which would
 * otherwise render unstyled).
 */
public final class ViewLoader {

    private static final String VIEW_PACKAGE = "/rw/smart/ecommerce/";
    private static final String APP_STYLESHEET = VIEW_PACKAGE + "styles/app.css";

    private ViewLoader() {
        // utility class, no instances
    }

    /** A loaded view: its node graph plus the controller FXML instantiated for it. */
    public record View<C>(Parent root, C controller) {
    }

    public static <C> View<C> load(String view) {
        URL location = ViewLoader.class.getResource(VIEW_PACKAGE + view);
        if (location == null) throw new IllegalStateException("View not found on the classpath: " + view);

        FXMLLoader loader = new FXMLLoader(location);
        try {
            Parent root = loader.load();
            return new View<>(root, loader.getController());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load view: " + view, e);
        }
    }

    public static Scene scene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
        scene.getStylesheets().add(ViewLoader.class.getResource(APP_STYLESHEET).toExternalForm());
        return scene;
    }

    /**
     * Replaces the scene on an existing stage — used for login -> main shell and
     * logout -> login, where the whole window changes rather than a panel.
     */
    public static <C> C swapScene(Stage stage, String view, String title, double width, double height) {
        View<C> loaded = load(view);
        stage.setScene(scene(loaded.root(), width, height));
        stage.setTitle(title);
        stage.centerOnScreen();
        return loaded.controller();
    }

    /**
     * Opens a view as a modal dialog and blocks until it is closed.
     *
     * @param initializer runs after the controller's initialize() and before the
     *                    dialog is shown, so callers can seed it with context
     *                    (the product being edited, the order being viewed, ...)
     * @return the dialog's controller, so callers can read back its result
     */
    public static <C> C openModal(Window owner, String view, String title, Consumer<C> initializer) {
        View<C> loaded = load(view);
        if (initializer != null) {
            initializer.accept(loaded.controller());
        }

        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        stage.setTitle(title);
        stage.setScene(scene(loaded.root(), -1, -1));
        stage.sizeToScene();
        stage.showAndWait();
        return loaded.controller();
    }
}
