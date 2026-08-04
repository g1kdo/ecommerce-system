package rw.smart.ecommerce.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import rw.smart.ecommerce.core.inventory.model.Inventory;
import rw.smart.ecommerce.core.inventory.service.InventoryService;
import rw.smart.ecommerce.core.log.enums.EventType;
import rw.smart.ecommerce.core.log.service.LogService;
import rw.smart.ecommerce.core.product.model.Product;
import rw.smart.ecommerce.core.product.service.ProductService;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;
import rw.smart.ecommerce.utils.ui.Notifier;
import rw.smart.ecommerce.utils.ui.RefreshableView;

import java.time.format.DateTimeFormatter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stock management screen. Products come from ProductService (cache-backed) and
 * quantities from InventoryService in a single query, so the table costs two
 * round trips regardless of how many products are listed.
 */
public class InventoryListController implements RefreshableView {

    /** Low-stock warning threshold used for row highlighting and filtering. */
    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private BorderPane rootPane;
    @FXML private HBox toolbar;
    @FXML private HBox footerBar;
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private CheckBox lowStockOnlyCheckBox;
    @FXML private Spinner<Integer> quantitySpinner;
    @FXML private Button updateStockButton;
    @FXML private Button refreshButton;
    @FXML private TableView<StockRow> stockTable;
    @FXML private TableColumn<StockRow, String> productColumn;
    @FXML private TableColumn<StockRow, String> skuColumn;
    @FXML private TableColumn<StockRow, String> quantityColumn;
    @FXML private TableColumn<StockRow, String> updatedColumn;
    @FXML private Label summaryLabel;

    private final ProductService productService = new ProductService();
    private final InventoryService inventoryService = new InventoryService();
    private final LogService logService = new LogService();

    /** One table row: a product plus its (possibly absent) stock record. */
    public static class StockRow {
        private final Product product;
        private final Inventory inventory;

        StockRow(Product product, Inventory inventory) {
            this.product = product;
            this.inventory = inventory;
        }

        public Product getProduct() {
            return product;
        }

        public int getQuantity() {
            return inventory == null ? 0 : inventory.getQuantity();
        }

        String getLastUpdated() {
            if (inventory == null || inventory.getLastUpdated() == null) return "Not tracked yet";

            return inventory.getLastUpdated().format(TIMESTAMP_FORMAT);
        }

        boolean isLowStock() {
            return getQuantity() <= LOW_STOCK_THRESHOLD;
        }
    }

    @FXML
    public void initialize() {
        configureUi();

        productColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProduct().getName()));
        skuColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProduct().getSku()));
        quantityColumn.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getQuantity())));
        updatedColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getLastUpdated()));

        // keep the spinner in step with the selected row so "Update Stock" starts
        // from the current value instead of resetting it
        stockTable.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> {
            if (newRow != null) {
                quantitySpinner.getValueFactory().setValue(newRow.getQuantity());
            }
        });

        lowStockOnlyCheckBox.selectedProperty().addListener((obs, wasSelected, isSelected) -> refresh());
        searchField.setOnAction(event -> refresh());

        refresh();
    }

    private void configureUi() {
        rootPane.getStyleClass().add("content-pane");
        toolbar.getStyleClass().add("toolbar-panel");
        footerBar.getStyleClass().add("footer-panel");
        stockTable.getStyleClass().add("product-table");
        stockTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        stockTable.setPlaceholder(new Label("No products match the current filters."));
        stockTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(StockRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().remove("low-stock-row");
                if (!empty && row != null && row.isLowStock()) {
                    getStyleClass().add("low-stock-row");
                }
            }
        });

        quantitySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100_000, 0));

        styleButton(searchButton, FontAwesomeSolid.SEARCH, "btn", "btn-primary");
        styleButton(updateStockButton, FontAwesomeSolid.CUBES, "btn", "btn-success");
        styleButton(refreshButton, FontAwesomeSolid.SYNC_ALT, "btn", "btn-default");

        Tooltip.install(quantitySpinner, new Tooltip("Absolute quantity to store for the selected product"));
    }

    private void styleButton(Button button, FontAwesomeSolid icon, String... styleClasses) {
        button.getStyleClass().addAll(styleClasses);
        button.setGraphic(new FontIcon(icon));
        button.setContentDisplay(ContentDisplay.LEFT);
    }

    @Override
    public void onShown() {
        refresh();
    }

    @FXML
    private void onSearch() {
        refresh();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    private void refresh() {
        try {
            List<Product> products = productService.sortByName(productService.search(searchField.getText()));
            Map<Integer, Inventory> stockByProduct = inventoryService.getStockByProduct();

            List<StockRow> rows = new ArrayList<>();
            int lowStockCount = 0;
            int totalUnits = 0;
            for (Product product : products) {
                StockRow row = new StockRow(product, stockByProduct.get(product.getProductId()));
                totalUnits += row.getQuantity();
                if (row.isLowStock()) {
                    lowStockCount++;
                }
                if (!lowStockOnlyCheckBox.isSelected() || row.isLowStock()) {
                    rows.add(row);
                }
            }

            stockTable.setItems(FXCollections.observableArrayList(rows));
            summaryLabel.setText(products.size() + " products tracked - " + totalUnits
                    + " units in stock - " + lowStockCount + " at or below " + LOW_STOCK_THRESHOLD);
        } catch (SQLException e) {
            Notifier.error("Failed to load inventory", e);
        }
    }

    @FXML
    private void onUpdateStock() {
        StockRow selected = stockTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Notifier.warn("Select a product to update its stock.");
            return;
        }

        try {
            int quantity = quantitySpinner.getValue();
            inventoryService.setStock(selected.getProduct().getProductId(), quantity);
            logService.log(EventType.STOCK_UPDATED, Map.of(
                    "product_id", selected.getProduct().getProductId(),
                    "quantity", quantity,
                    "previous_quantity", selected.getQuantity()));
            Notifier.info(selected.getProduct().getName() + " stock set to " + quantity + ".");
            refresh();
        } catch (InvalidInputException e) {
            Notifier.warn(e.getMessage());
        } catch (SQLException e) {
            Notifier.error("Stock update failed", e);
        }
    }
}
