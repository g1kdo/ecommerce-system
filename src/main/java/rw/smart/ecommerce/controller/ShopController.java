package rw.smart.ecommerce.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import rw.smart.ecommerce.core.category.model.Category;
import rw.smart.ecommerce.core.category.service.CategoryService;
import rw.smart.ecommerce.core.inventory.model.Inventory;
import rw.smart.ecommerce.core.inventory.service.InventoryService;
import rw.smart.ecommerce.core.log.enums.EventType;
import rw.smart.ecommerce.core.log.service.LogService;
import rw.smart.ecommerce.core.order.model.Order;
import rw.smart.ecommerce.core.order.model.item.CartItem;
import rw.smart.ecommerce.core.order.service.OrderService;
import rw.smart.ecommerce.core.product.model.Product;
import rw.smart.ecommerce.core.product.service.ProductService;
import rw.smart.ecommerce.utils.exceptions.InsufficientStockException;
import rw.smart.ecommerce.utils.session.Session;
import rw.smart.ecommerce.utils.ui.Money;
import rw.smart.ecommerce.utils.ui.Notifier;
import rw.smart.ecommerce.utils.ui.RefreshableView;
import rw.smart.ecommerce.utils.ui.ViewLoader;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Customer-facing screen: browse the catalogue, build a cart, and check out
 * through OrderService (which writes the order and decrements stock in one
 * transaction). Stock shown here is advisory — the authoritative check happens
 * inside that transaction, so a concurrent sale still fails cleanly.
 */
public class ShopController implements RefreshableView {

    @FXML private BorderPane rootPane;
    @FXML private HBox toolbar;
    @FXML private SplitPane splitPane;
    @FXML private VBox cartPanel;
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private ComboBox<Category> categoryFilter;
    @FXML private ComboBox<String> sortOptions;
    @FXML private Spinner<Integer> quantitySpinner;
    @FXML private Button addToCartButton;
    @FXML private Button reviewsButton;

    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, String> nameColumn;
    @FXML private TableColumn<Product, String> categoryColumn;
    @FXML private TableColumn<Product, String> priceColumn;
    @FXML private TableColumn<Product, String> stockColumn;

    @FXML private TableView<CartItem> cartTable;
    @FXML private TableColumn<CartItem, String> cartProductColumn;
    @FXML private TableColumn<CartItem, String> cartQuantityColumn;
    @FXML private TableColumn<CartItem, String> cartUnitPriceColumn;
    @FXML private TableColumn<CartItem, String> cartLineTotalColumn;
    @FXML private Label cartTotalLabel;
    @FXML private Button removeLineButton;
    @FXML private Button clearCartButton;
    @FXML private Button placeOrderButton;

    private final ProductService productService = new ProductService();
    private final CategoryService categoryService = new CategoryService();
    private final InventoryService inventoryService = new InventoryService();
    private final OrderService orderService = new OrderService();
    private final LogService logService = new LogService();

    private final Map<Integer, String> categoryNamesById = new HashMap<>();
    private final Map<Integer, Integer> stockByProductId = new HashMap<>();
    private final ObservableList<CartItem> cart = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        configureUi();

        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        priceColumn.setCellValueFactory(data -> new SimpleStringProperty(Money.format(data.getValue().getPrice())));
        categoryColumn.setCellValueFactory(data -> new SimpleStringProperty(categoryNameOf(data.getValue())));
        stockColumn.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(availableStock(data.getValue().getProductId()))));

        cartProductColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProduct().getName()));
        cartQuantityColumn.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getQuantity())));
        cartUnitPriceColumn.setCellValueFactory(data -> new SimpleStringProperty(Money.format(data.getValue().getUnitPrice())));
        cartLineTotalColumn.setCellValueFactory(data -> new SimpleStringProperty(Money.format(data.getValue().getLineTotal())));
        cartTable.setItems(cart);
        cart.addListener((javafx.collections.ListChangeListener<CartItem>) change -> updateCartTotal());

        sortOptions.setItems(FXCollections.observableArrayList("Name (A-Z)", "Price (Low-High)", "Price (High-Low)"));
        sortOptions.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> refreshProducts());
        searchField.setOnAction(event -> refreshProducts());
        productTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                onAddToCart();
            }
        });

        loadCategories();
        refreshProducts();
        updateCartTotal();
    }

    private void configureUi() {
        rootPane.getStyleClass().add("content-pane");
        toolbar.getStyleClass().add("toolbar-panel");
        cartPanel.getStyleClass().add("cart-panel");
        splitPane.getStyleClass().add("shop-split");

        productTable.getStyleClass().add("product-table");
        productTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        productTable.setPlaceholder(new Label("No products match the current filters."));

        cartTable.getStyleClass().add("product-table");
        cartTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        cartTable.setPlaceholder(new Label("Your cart is empty."));

        quantitySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1_000, 1));
        categoryFilter.setPromptText("All categories");

        styleButton(searchButton, FontAwesomeSolid.SEARCH, "btn", "btn-primary");
        styleButton(addToCartButton, FontAwesomeSolid.CART_PLUS, "btn", "btn-success");
        styleButton(reviewsButton, FontAwesomeSolid.STAR, "btn", "btn-info");
        styleButton(removeLineButton, FontAwesomeSolid.TRASH, "btn", "btn-danger");
        styleButton(clearCartButton, FontAwesomeSolid.BROOM, "btn", "btn-default");
        styleButton(placeOrderButton, FontAwesomeSolid.PAPER_PLANE, "btn", "btn-primary");

        Tooltip.install(quantitySpinner, new Tooltip("How many units to add to the cart"));
        Tooltip.install(searchField, new Tooltip("Press Enter or click Search"));
    }

    private void styleButton(Button button, FontAwesomeSolid icon, String... styleClasses) {
        button.getStyleClass().addAll(styleClasses);
        button.setGraphic(new FontIcon(icon));
        button.setContentDisplay(ContentDisplay.LEFT);
        // this screen is control-heavy; never shrink labels down to "Add to..."
        button.setMinWidth(Region.USE_PREF_SIZE);
    }

    private void loadCategories() {
        try {
            List<Category> categories = categoryService.getAllCategories();
            categoryNamesById.clear();
            for (Category category : categories) {
                categoryNamesById.put(category.getCategoryId(), category.getName());
            }
            categoryFilter.setItems(FXCollections.observableArrayList(categories));
            categoryFilter.getSelectionModel().selectedItemProperty()
                    .addListener((obs, oldValue, newValue) -> refreshProducts());
        } catch (SQLException e) {
            Notifier.error("Failed to load categories", e);
        }
    }

    private void refreshProducts() {
        try {
            reloadStock();

            // captured before the search: a warm cache means it was served from memory
            boolean cacheWarm = productService.isCacheWarm();
            String searchTerm = searchField.getText();
            List<Product> products = productService.search(searchTerm);
            if (searchTerm != null && !searchTerm.isBlank()) {
                logService.log(EventType.PRODUCT_SEARCH, Map.of(
                        "query", searchTerm.trim(),
                        "results_count", products.size(),
                        "cache_hit", cacheWarm));
            }
            Category selectedCategory = categoryFilter.getValue();
            if (selectedCategory != null) {
                products = products.stream()
                        .filter(product -> product.getCategoryId() == selectedCategory.getCategoryId())
                        .collect(Collectors.toList());
            }

            productTable.setItems(FXCollections.observableArrayList(applySortIfSelected(products)));
        } catch (SQLException e) {
            Notifier.error("Failed to load products", e);
        }
    }

    private void reloadStock() throws SQLException {
        Map<Integer, Inventory> stock = inventoryService.getStockByProduct();
        stockByProductId.clear();
        stock.forEach((productId, inventory) -> stockByProductId.put(productId, inventory.getQuantity()));
    }

    private List<Product> applySortIfSelected(List<Product> products) {
        String selected = sortOptions.getValue();
        if (selected == null) {
            return productService.sortByName(products);
        }
        return switch (selected) {
            case "Price (Low-High)" -> productService.sortByPrice(products, true);
            case "Price (High-Low)" -> productService.sortByPrice(products, false);
            default -> productService.sortByName(products);
        };
    }

    private String categoryNameOf(Product product) {
        String name = categoryNamesById.get(product.getCategoryId());
        return name == null ? String.valueOf(product.getCategoryId()) : name;
    }

    private int availableStock(int productId) {
        return stockByProductId.getOrDefault(productId, 0);
    }

    /** Re-entering the screen reloads prices and stock but keeps the cart intact. */
    @Override
    public void onShown() {
        refreshProducts();
    }

    @FXML
    private void onSearch() {
        refreshProducts();
    }

    @FXML
    private void onAddToCart() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Notifier.warn("Select a product to add to the cart.");
            return;
        }

        int requested = quantitySpinner.getValue();
        int alreadyInCart = findCartLine(selected.getProductId()).map(CartItem::getQuantity).orElse(0);
        int available = availableStock(selected.getProductId());
        if (alreadyInCart + requested > available) {
            Notifier.warn("Only " + available + " unit(s) of " + selected.getName()
                    + " in stock" + (alreadyInCart > 0 ? " (" + alreadyInCart + " already in your cart)." : "."));
            return;
        }

        Optional<CartItem> existing = findCartLine(selected.getProductId());
        if (existing.isPresent()) {
            existing.get().addQuantity(requested);
            cartTable.refresh();
            updateCartTotal();
        } else {
            cart.add(new CartItem(selected, requested));
        }
        Notifier.info(requested + " x " + selected.getName() + " added to your cart.");
    }

    private Optional<CartItem> findCartLine(int productId) {
        return cart.stream().filter(line -> line.getProductId() == productId).findFirst();
    }

    @FXML
    private void onRemoveLine() {
        CartItem selected = cartTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Notifier.warn("Select a cart line to remove.");
            return;
        }
        cart.remove(selected);
    }

    @FXML
    private void onClearCart() {
        if (cart.isEmpty()) {
            return;
        }
        if (Notifier.confirm("Empty your cart?", "All " + cart.size() + " line(s) will be discarded.")) {
            cart.clear();
        }
    }

    @FXML
    private void onPlaceOrder() {
        if (cart.isEmpty()) {
            Notifier.warn("Add at least one product before placing an order.");
            return;
        }

        String total = Money.format(cartTotal());
        if (!Notifier.confirm("Place this order?", cart.size() + " line(s) for a total of " + total + ".")) {
            return;
        }

        Order order = new Order();
        order.setUserId(Session.currentUserId());
        order.setItems(cart.stream().map(CartItem::toOrderItem).collect(Collectors.toList()));

        int lineCount = cart.size();
        try {
            int orderId = orderService.placeOrder(order);
            logService.log(EventType.ORDER_PLACED, Map.of(
                    "order_id", orderId, "total", total, "lines", lineCount));
            cart.clear();
            refreshProducts();
            Notifier.info("Order #" + orderId + " placed for " + total + ".");
        } catch (InsufficientStockException e) {
            // the order transaction was rolled back, so nothing was reserved
            logService.log(EventType.ORDER_REJECTED, Map.of("reason", "insufficient_stock", "lines", lineCount));
            Notifier.warn("Someone bought the last units first - stock is no longer sufficient. Nothing was charged.");
            refreshProducts();
        } catch (SQLException e) {
            Notifier.error("Could not place the order", e);
        }
    }

    @FXML
    private void onOpenReviews() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Notifier.warn("Select a product to see its reviews.");
            return;
        }

        ViewLoader.<ProductReviewsController>openModal(
                rootPane.getScene().getWindow(),
                "product_reviews.fxml",
                "Reviews - " + selected.getName(),
                controller -> controller.loadProduct(selected));
    }

    private BigDecimal cartTotal() {
        return cart.stream()
                .map(CartItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void updateCartTotal() {
        cartTotalLabel.setText(Money.format(cartTotal()));
    }
}
