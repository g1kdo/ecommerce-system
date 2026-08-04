package rw.smart.ecommerce.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import rw.smart.ecommerce.core.category.model.Category;
import rw.smart.ecommerce.core.category.service.CategoryService;
import rw.smart.ecommerce.core.product.model.Product;
import rw.smart.ecommerce.core.product.service.ProductService;
import rw.smart.ecommerce.utils.ui.Notifier;
import rw.smart.ecommerce.utils.ui.RefreshableView;
import rw.smart.ecommerce.utils.ui.ViewLoader;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller layer — no JDBC/SQL here. All data access is delegated to
 * ProductService / CategoryService, keeping the layered architecture intact.
 */
public class ProductListController implements RefreshableView {

    private static final String FOREIGN_KEY_VIOLATION = "23503";

    @FXML private BorderPane rootPane;
    @FXML private HBox toolbar;
    @FXML private HBox footerBar;
    @FXML private TextField searchField;
    @FXML private ComboBox<Category> categoryFilter;
    @FXML private ComboBox<String> sortOptions;
    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, String> nameColumn;
    @FXML private TableColumn<Product, String> skuColumn;
    @FXML private TableColumn<Product, String> priceColumn;
    @FXML private TableColumn<Product, String> categoryColumn;
    @FXML private Label pageLabel;
    @FXML private Button searchButton;
    @FXML private Button newProductButton;
    @FXML private Button editProductButton;
    @FXML private Button deleteProductButton;
    @FXML private Button prevPageButton;
    @FXML private Button nextPageButton;

    private final ProductService productService = new ProductService();
    private final CategoryService categoryService = new CategoryService();
    private final Map<Integer, String> categoryNamesById = new HashMap<>();

    private static final int PAGE_SIZE = 20;
    private int currentPage = 0;

    @FXML
    public void initialize() {
        productTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        configureUi();

        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        skuColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSku()));
        priceColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPrice().toString()));
        categoryColumn.setCellValueFactory(data -> {
            int categoryId = data.getValue().getCategoryId();
            String categoryName = categoryNamesById.get(categoryId);
            return new SimpleStringProperty(categoryName != null ? categoryName : String.valueOf(categoryId));
        });

        sortOptions.setItems(FXCollections.observableArrayList("Name (A-Z)", "Price (Low-High)", "Price (High-Low)"));
        sortOptions.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            currentPage = 0;
            refreshProducts();
        });

        categoryFilter.setPromptText("All categories");
        searchField.setOnAction(event -> onSearch());

        loadCategories();
        refreshProducts();
    }

    private void configureUi() {
        rootPane.getStyleClass().add("content-pane");
        toolbar.getStyleClass().add("toolbar-panel");
        footerBar.getStyleClass().add("footer-panel");
        productTable.getStyleClass().add("product-table");
        productTable.setPlaceholder(new Label("No products match the current filters."));

        stylePrimary(searchButton, FontAwesomeSolid.SEARCH, "btn btn-primary");
        stylePrimary(newProductButton, FontAwesomeSolid.PLUS, "btn btn-success");
        stylePrimary(editProductButton, FontAwesomeSolid.EDIT, "btn btn-info");
        stylePrimary(deleteProductButton, FontAwesomeSolid.TRASH, "btn btn-danger");
        stylePrimary(prevPageButton, FontAwesomeSolid.CHEVRON_LEFT, "btn btn-default");
        stylePrimary(nextPageButton, FontAwesomeSolid.CHEVRON_RIGHT, "btn btn-default");

        Tooltip.install(searchField, new Tooltip("Press Enter or click Search"));
        Tooltip.install(categoryFilter, new Tooltip("Filter the table by category"));
        Tooltip.install(sortOptions, new Tooltip("Sort the visible results"));
    }

    private void stylePrimary(Button button, FontAwesomeSolid icon, String... styleClasses) {
        if (button == null) {
            return;
        }
        button.getStyleClass().addAll(styleClasses);
        button.setGraphic(new FontIcon(icon));
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setMaxWidth(Double.MAX_VALUE);
    }

    private void loadCategories() {
        try {
            List<Category> categories = categoryService.getAllCategories();
            categoryNamesById.clear();
            for (Category category : categories) {
                categoryNamesById.put(category.getCategoryId(), category.getName());
            }
            categoryFilter.setItems(FXCollections.observableArrayList(categories));
            categoryFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
                currentPage = 0;
                refreshProducts();
            });
        } catch (SQLException e) {
            Notifier.error("Failed to load categories", e);
        }
    }

    private void refreshProducts() {
        try {
            List<Product> products = productService.search(searchField.getText());
            Category selectedCategory = categoryFilter.getValue();
            if (selectedCategory != null) {
                products = products.stream()
                        .filter(product -> product.getCategoryId() == selectedCategory.getCategoryId())
                        .collect(Collectors.toList());
            }

            products = applySortIfSelected(products);

            int totalPages = Math.max(1, (int) Math.ceil(products.size() / (double) PAGE_SIZE));
            if (currentPage >= totalPages) {
                currentPage = totalPages - 1;
            }

            int fromIndex = currentPage * PAGE_SIZE;
            int toIndex = Math.min(fromIndex + PAGE_SIZE, products.size());
            List<Product> pageItems = fromIndex >= products.size() ? List.of() : products.subList(fromIndex, toIndex);
            renderTable(pageItems, totalPages);
        } catch (SQLException e) {
            Notifier.error("Failed to load products", e);
        }
    }

    private void renderTable(List<Product> products, int totalPages) {
        javafx.collections.ObservableList<Product> observableList = FXCollections.observableArrayList(products);
        productTable.setItems(observableList);
        pageLabel.setText("Page " + (currentPage + 1) + " of " + totalPages);
    }

    /**
     * Re-entering the screen reloads the table. The category filter is not
     * reloaded here — {@link #loadCategories()} attaches a selection listener and
     * must stay a one-time call.
     */
    @Override
    public void onShown() {
        refreshProducts();
    }

    @FXML
    private void onSearch() {
        currentPage = 0;
        refreshProducts();
    }

    private List<Product> applySortIfSelected(List<Product> products) {
        String selected = sortOptions.getValue();
        if (selected == null) {
            return products;
        }
        return switch (selected) {
            case "Name (A-Z)" -> productService.sortByName(products);
            case "Price (Low-High)" -> productService.sortByPrice(products, true);
            case "Price (High-Low)" -> productService.sortByPrice(products, false);
            default -> products;
        };
    }

    @FXML
    private void onNewProduct() {
        showProductForm(null);
    }

    @FXML
    private void onEditProduct() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Notifier.warn("Select a product to edit.");
            return;
        }
        showProductForm(selected);
    }

    @FXML
    private void onDeleteProduct() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Notifier.warn("Select a product to delete.");
            return;
        }
        if (!Notifier.confirm("Delete " + selected.getName() + "?", "This action cannot be undone.")) {
            return;
        }
        try {
            productService.deleteProduct(selected.getProductId());
            Notifier.info("Product deleted successfully.");
            refreshProducts();
        } catch (SQLException e) {
            if (FOREIGN_KEY_VIOLATION.equals(e.getSQLState())) {
                Notifier.warn("This product appears on an existing order and cannot be deleted.");
            } else {
                Notifier.error("Delete failed", e);
            }
        }
    }

    @FXML
    private void onPrevPage() {
        if (currentPage > 0) {
            currentPage--;
            refreshProducts();
        }
    }

    @FXML
    private void onNextPage() {
        currentPage++;
        refreshProducts();
    }

    private void showProductForm(Product product) {
        ViewLoader.<ProductFormController>openModal(
                rootPane.getScene().getWindow(),
                "product_form.fxml",
                product == null ? "New Product" : "Edit Product",
                controller -> {
                    if (product != null) {
                        controller.loadProduct(product);
                    }
                });
        refreshProducts();
    }
}
