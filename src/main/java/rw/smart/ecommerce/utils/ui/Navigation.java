package rw.smart.ecommerce.utils.ui;

import javafx.scene.Node;
import javafx.stage.Stage;

/**
 * Top-level screen transitions (the ones that replace the whole window).
 * Panel-level navigation inside the signed-in area lives in MainShellController.
 */
public final class Navigation {

    public static final String LOGIN_VIEW = "login.fxml";
    public static final String REGISTER_VIEW = "register.fxml";
    public static final String MAIN_SHELL_VIEW = "main_shell.fxml";

    private Navigation() {
        // utility class, no instances
    }

    public static void showLogin(Stage stage) {
        resize(stage, 520, 620);
        ViewLoader.swapScene(stage, LOGIN_VIEW, "Smart E-Commerce - Sign In", 520, 620);
    }

    public static void showRegister(Stage stage) {
        resize(stage, 560, 780);
        ViewLoader.swapScene(stage, REGISTER_VIEW, "Smart E-Commerce - Create Account", 560, 780);
    }

    public static void showMainShell(Stage stage) {
        resize(stage, 1024, 680);
        ViewLoader.swapScene(stage, MAIN_SHELL_VIEW, "Smart E-Commerce System", 1180, 760);
        stage.setMaximized(true);
    }

    /** The stage a control currently lives in — controllers only ever know their nodes. */
    public static Stage stageOf(Node node) {
        return (Stage) node.getScene().getWindow();
    }

    private static void resize(Stage stage, double minWidth, double minHeight) {
        // lower the floor before the scene swap, otherwise the previous screen's
        // minimum keeps a smaller screen oversized
        stage.setMaximized(false);
        stage.setMinWidth(minWidth);
        stage.setMinHeight(minHeight);
    }
}
