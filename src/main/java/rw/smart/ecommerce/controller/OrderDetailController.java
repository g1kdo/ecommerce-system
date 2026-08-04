package rw.smart.ecommerce.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import rw.smart.ecommerce.core.order.model.Order;
import rw.smart.ecommerce.core.order.model.item.OrderItem;
import rw.smart.ecommerce.core.order.service.OrderService;
import rw.smart.ecommerce.core.product.model.Product;
import rw.smart.ecommerce.core.product.service.ProductService;
import rw.smart.ecommerce.utils.ui.Money;
import rw.smart.ecommerce.utils.ui.Notifier;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only view of one order and its lines. Product names are resolved through
 * the cache-backed ProductService, so listing items costs no extra queries once
 * the cache is warm.
 */
public class OrderDetailController {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private VBox detailRoot;
    @FXML private Label titleLabel;
    @FXML private Label dateLabel;
    @FXML private Label statusLabel;
    @FXML private Label totalLabel;
    @FXML private TableView<OrderItem> itemTable;
    @FXML private TableColumn<OrderItem, String> productColumn;
    @FXML private TableColumn<OrderItem, String> quantityColumn;
    @FXML private TableColumn<OrderItem, String> unitPriceColumn;
    @FXML private TableColumn<OrderItem, String> lineTotalColumn;
    @FXML private Button closeButton;

    private final OrderService orderService = new OrderService();
    private final ProductService productService = new ProductService();
    private final Map<Integer, String> productNamesById = new HashMap<>();

    @FXML
    public void initialize() {
        detailRoot.getStyleClass().add("form-shell");
        itemTable.getStyleClass().add("product-table");
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        itemTable.setPlaceholder(new Label("This order has no items."));

        closeButton.getStyleClass().addAll("btn", "btn-default");
        closeButton.setGraphic(new FontIcon(FontAwesomeSolid.TIMES));
        closeButton.setContentDisplay(ContentDisplay.LEFT);

        productColumn.setCellValueFactory(data ->
                new SimpleStringProperty(productNamesById.getOrDefault(
                        data.getValue().getProductId(), "Product #" + data.getValue().getProductId())));
        quantityColumn.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getQuantity())));
        unitPriceColumn.setCellValueFactory(data -> new SimpleStringProperty(Money.format(data.getValue().getUnitPrice())));
        lineTotalColumn.setCellValueFactory(data -> new SimpleStringProperty(Money.format(data.getValue().getLineTotal())));
    }

    /** Called by OrderListController before the dialog is shown. */
    public void loadOrder(Order order) {
        titleLabel.setText("Order #" + order.getOrderId());
        dateLabel.setText(order.getOrderDate() == null ? "" : order.getOrderDate().format(TIMESTAMP_FORMAT));
        statusLabel.setText(order.getStatus().toString());
        totalLabel.setText(Money.format(order.getTotalAmount()));

        try {
            List<OrderItem> items = orderService.getOrderItems(order.getOrderId());
            productNamesById.clear();
            for (OrderItem item : items) {
                Product product = productService.getProduct(item.getProductId());
                if (product != null) {
                    productNamesById.put(item.getProductId(), product.getName());
                }
            }
            itemTable.setItems(FXCollections.observableArrayList(items));
        } catch (SQLException e) {
            Notifier.error("Failed to load order items", e);
        }
    }

    @FXML
    private void onClose() {
        ((Stage) detailRoot.getScene().getWindow()).close();
    }
}
