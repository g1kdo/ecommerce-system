package rw.smart.ecommerce.controller;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import rw.smart.ecommerce.core.log.enums.EventType;
import rw.smart.ecommerce.core.log.service.LogService;
import rw.smart.ecommerce.utils.session.Session;
import rw.smart.ecommerce.utils.ui.Navigation;
import rw.smart.ecommerce.utils.ui.Notifier;
import rw.smart.ecommerce.utils.ui.RefreshableView;
import rw.smart.ecommerce.utils.ui.ViewLoader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Frame for the signed-in area: header, sidebar, and a content region that the
 * feature screens are swapped into. Each nav entry maps to one FXML view.
 */
public class MainShellController {

    private static final String ACTIVE_NAV_CLASS = "nav-active";

    @FXML private BorderPane rootPane;
    @FXML private HBox headerBar;
    @FXML private VBox navBar;
    @FXML private StackPane contentArea;
    @FXML private Label userLabel;
    @FXML private Button logoutButton;
    @FXML private Button shopNavButton;
    @FXML private Button ordersNavButton;
    @FXML private Button productsNavButton;
    @FXML private Button categoriesNavButton;
    @FXML private Button inventoryNavButton;
    @FXML private Button activityNavButton;
    @FXML private Button profileNavButton;

    private final LogService logService = new LogService();
    private final Map<String, Parent> loadedViews = new HashMap<>();
    private final Map<String, Object> viewControllers = new HashMap<>();

    private List<Button> navButtons;

    @FXML
    public void initialize() {
        rootPane.getStyleClass().add("app-shell");
        headerBar.getStyleClass().add("header-panel");
        navBar.getStyleClass().add("nav-panel");

        logoutButton.getStyleClass().addAll("btn", "btn-default");
        logoutButton.setGraphic(new FontIcon(FontAwesomeSolid.SIGN_OUT_ALT));
        logoutButton.setContentDisplay(ContentDisplay.LEFT);

        navButtons = List.of(shopNavButton, ordersNavButton, productsNavButton,
                categoriesNavButton, inventoryNavButton, activityNavButton, profileNavButton);
        styleNav(shopNavButton, FontAwesomeSolid.STORE);
        styleNav(ordersNavButton, FontAwesomeSolid.RECEIPT);
        styleNav(productsNavButton, FontAwesomeSolid.BOX_OPEN);
        styleNav(categoriesNavButton, FontAwesomeSolid.TAGS);
        styleNav(inventoryNavButton, FontAwesomeSolid.WAREHOUSE);
        styleNav(activityNavButton, FontAwesomeSolid.HISTORY);
        styleNav(profileNavButton, FontAwesomeSolid.USER);

        userLabel.setText("Signed in as " + Session.currentUser().getFullName());
        onShowShop();
    }

    @FXML
    private void onShowShop() {
        showView("shop.fxml", shopNavButton);
    }

    @FXML
    private void onShowOrders() {
        showView("order_list.fxml", ordersNavButton);
    }

    @FXML
    private void onShowProducts() {
        showView("product_list.fxml", productsNavButton);
    }

    @FXML
    private void onShowCategories() {
        showView("category_list.fxml", categoriesNavButton);
    }

    @FXML
    private void onShowInventory() {
        showView("inventory_list.fxml", inventoryNavButton);
    }

    @FXML
    private void onShowActivityLog() {
        showView("activity_log.fxml", activityNavButton);
    }

    @FXML
    private void onShowProfile() {
        showView("profile.fxml", profileNavButton);
    }

    @FXML
    private void onLogout() {
        if (!Notifier.confirm("Sign out?", "You will need to sign in again to continue shopping.")) {
            return;
        }
        // logged before the session is cleared, so the event still carries the user
        logService.log(EventType.LOGOUT, Map.of());
        Session.logout();
        Navigation.showLogin(Navigation.stageOf(rootPane));
    }

    /**
     * Shows a screen, building it on first visit and reusing it afterwards so
     * per-screen state (the cart, filters, selections) is not thrown away by
     * navigation. Reused screens are asked to reload their data on the way in.
     */
    private void showView(String view, Button navButton) {
        try {
            Parent root = loadedViews.get(view);
            if (root == null) {
                ViewLoader.View<Object> loaded = ViewLoader.load(view);
                root = loaded.root();
                loadedViews.put(view, root);
                viewControllers.put(view, loaded.controller());
            } else if (viewControllers.get(view) instanceof RefreshableView refreshable) {
                refreshable.onShown();
            }

            contentArea.getChildren().setAll(root);
            userLabel.setText("Signed in as " + Session.currentUser().getFullName());
            markActive(navButton);
        } catch (RuntimeException e) {
            Notifier.error("Could not open that screen", e);
        }
    }

    private void markActive(Button active) {
        for (Button navButton : navButtons) {
            navButton.getStyleClass().remove(ACTIVE_NAV_CLASS);
        }
        active.getStyleClass().add(ACTIVE_NAV_CLASS);
    }

    private void styleNav(Button button, FontAwesomeSolid icon) {
        button.getStyleClass().add("nav-button");
        button.setGraphic(new FontIcon(icon));
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    }
}
