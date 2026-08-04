package rw.smart.ecommerce.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import rw.smart.ecommerce.core.category.model.Category;
import rw.smart.ecommerce.core.category.service.CategoryService;
import rw.smart.ecommerce.core.log.enums.EventType;
import rw.smart.ecommerce.core.log.service.LogService;
import rw.smart.ecommerce.core.product.model.Product;
import rw.smart.ecommerce.core.product.service.ProductService;
import rw.smart.ecommerce.utils.ui.Notifier;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ProductFormController {
    @FXML private GridPane formRoot;
    @FXML private TextField nameField;
    @FXML private TextArea descriptionField;
    @FXML
    private TextField priceField;
    @FXML private TextField skuField;
    @FXML private ComboBox<Category> categoryComboBox;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private final ProductService productService = new ProductService();
    private final CategoryService categoryService = new CategoryService();
    private final LogService logService = new LogService();

    private Product editingProduct; // null = create mode, non-null = edit mode

    @FXML
    public void initialize() {
        configureUi();

        try {
            List<Category> categories = categoryService.getAllCategories();
            categoryComboBox.setItems(FXCollections.observableArrayList(categories));
        } catch (SQLException e) {
            Notifier.error("Failed to load categories", e);
        }
    }

    private void configureUi() {
        formRoot.getStyleClass().add("form-shell");
        saveButton.getStyleClass().addAll("btn", "btn-success");
        cancelButton.getStyleClass().addAll("btn", "btn-default");
        saveButton.setGraphic(new FontIcon(FontAwesomeSolid.SAVE));
        cancelButton.setGraphic(new FontIcon(FontAwesomeSolid.TIMES));
        saveButton.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        cancelButton.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);

        priceField.setTextFormatter(new TextFormatter<>(change -> {
            String text = change.getControlNewText();
            return text.matches("\\d*(\\.\\d{0,2})?") ? change : null;
        }));

        Tooltip.install(nameField, new Tooltip("Enter the product name"));
        Tooltip.install(priceField, new Tooltip("Enter a non-negative price"));
        Tooltip.install(skuField, new Tooltip("SKU must be unique"));
        Tooltip.install(categoryComboBox, new Tooltip("Choose the product category"));
    }

    /** Called by the caller (ProductListController) to switch into edit mode. */
    public void loadProduct(Product product) {
        this.editingProduct = product;
        nameField.setText(product.getName());
        descriptionField.setText(product.getDescription());
        priceField.setText(product.getPrice().toString());
        skuField.setText(product.getSku());
        categoryComboBox.getItems().stream()
                .filter(category -> category.getCategoryId() == product.getCategoryId())
                .findFirst()
                .ifPresent(categoryComboBox::setValue);
    }

    @FXML
    private void onSave() {
        try {
            Category selectedCategory = categoryComboBox.getValue();
            BigDecimal price = validateForm(selectedCategory);

            Product product = editingProduct == null ? new Product() : editingProduct;
            populateFromForm(product, selectedCategory, price);

            if (editingProduct == null) {
                productService.createProduct(product);
                logService.log(EventType.PRODUCT_CREATED,
                        Map.of("product_id", product.getProductId(), "sku", product.getSku()));
            } else {
                productService.updateProduct(product);
                logService.log(EventType.PRODUCT_UPDATED,
                        Map.of("product_id", product.getProductId(), "sku", product.getSku()));
            }

            Notifier.info("Product saved successfully.");
            closeWindow();
        } catch (NumberFormatException e) {
            Notifier.warn("Price must be a valid number.");
        } catch (IllegalArgumentException e) {
            Notifier.warn(e.getMessage());
        } catch (SQLException e) {
            if (isDuplicateKeyViolation(e)) {
                Notifier.warn("SKU already exists. Use a unique SKU.");
            } else {
                Notifier.error("Save failed", e);
            }
        }
    }

    @FXML
    private void onCancel() {
        closeWindow();
    }

    private BigDecimal validateForm(Category selectedCategory) {
        if (nameField.getText() == null || nameField.getText().isBlank()) {
            throw new IllegalArgumentException("Name is required.");
        }
        if (skuField.getText() == null || skuField.getText().isBlank()) {
            throw new IllegalArgumentException("SKU is required.");
        }
        if (priceField.getText() == null || priceField.getText().isBlank()) {
            throw new IllegalArgumentException("Price is required.");
        }
        if (selectedCategory == null) {
            throw new IllegalArgumentException("Please select a category.");
        }

        BigDecimal price = new BigDecimal(priceField.getText().trim());
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price cannot be negative.");
        }
        return price;
    }

    private void populateFromForm(Product product, Category category, BigDecimal price) {
        product.setName(nameField.getText().trim());
        product.setDescription(descriptionField.getText() == null ? null : descriptionField.getText().trim());
        product.setPrice(price);
        product.setSku(skuField.getText().trim());
        product.setCategoryId(category.getCategoryId());
    }

    private boolean isDuplicateKeyViolation(SQLException e) {
        return "23505".equals(e.getSQLState());
    }

    private void closeWindow() {
        Stage stage = (Stage) nameField.getScene().getWindow();
        stage.close();
    }
}
