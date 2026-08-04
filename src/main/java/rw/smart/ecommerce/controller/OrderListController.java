package rw.smart.ecommerce.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import rw.smart.ecommerce.core.order.enums.Status;
import rw.smart.ecommerce.core.order.model.Order;
import rw.smart.ecommerce.core.order.service.OrderService;
import rw.smart.ecommerce.utils.session.Session;
import rw.smart.ecommerce.utils.ui.Money;
import rw.smart.ecommerce.utils.ui.Notifier;
import rw.smart.ecommerce.utils.ui.RefreshableView;
import rw.smart.ecommerce.utils.ui.ViewLoader;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Order history for the signed-in user, backed by OrderService.getOrdersForUser.
 * Status changes go through OrderService.updateStatus; the item lines are shown
 * in a modal that reads OrderService.getOrderItems.
 */
public class OrderListController implements RefreshableView {

    private static final String ALL_STATUSES = "All statuses";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private BorderPane rootPane;
    @FXML private HBox toolbar;
    @FXML private HBox footerBar;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<Status> newStatusComboBox;
    @FXML private Button refreshButton;
    @FXML private Button updateStatusButton;
    @FXML private Button viewDetailsButton;
    @FXML private TableView<Order> orderTable;
    @FXML private TableColumn<Order, String> orderIdColumn;
    @FXML private TableColumn<Order, String> orderDateColumn;
    @FXML private TableColumn<Order, String> statusColumn;
    @FXML private TableColumn<Order, String> totalColumn;
    @FXML private Label summaryLabel;

    private final OrderService orderService = new OrderService();

    @FXML
    public void initialize() {
        configureUi();

        orderIdColumn.setCellValueFactory(data -> new SimpleStringProperty("#" + data.getValue().getOrderId()));
        orderDateColumn.setCellValueFactory(data -> {
            var orderDate = data.getValue().getOrderDate();
            return new SimpleStringProperty(orderDate == null ? "" : orderDate.format(TIMESTAMP_FORMAT));
        });
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus().toString()));
        totalColumn.setCellValueFactory(data -> new SimpleStringProperty(Money.format(data.getValue().getTotalAmount())));

        List<String> filterOptions = new ArrayList<>();
        filterOptions.add(ALL_STATUSES);
        for (Status status : Status.values()) {
            filterOptions.add(status.toString());
        }
        statusFilter.setItems(FXCollections.observableArrayList(filterOptions));
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> refresh());

        newStatusComboBox.setItems(FXCollections.observableArrayList(Status.values()));

        // keep the "change to" box aligned with whatever row is selected
        orderTable.getSelectionModel().selectedItemProperty().addListener((obs, oldOrder, newOrder) -> {
            if (newOrder != null) {
                newStatusComboBox.setValue(newOrder.getStatus());
            }
        });
        orderTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                onViewDetails();
            }
        });

        refresh();
    }

    private void configureUi() {
        rootPane.getStyleClass().add("content-pane");
        toolbar.getStyleClass().add("toolbar-panel");
        footerBar.getStyleClass().add("footer-panel");
        orderTable.getStyleClass().add("product-table");
        orderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        orderTable.setPlaceholder(new Label("No orders yet - place one from the Shop screen."));

        styleButton(refreshButton, FontAwesomeSolid.SYNC_ALT, "btn", "btn-default");
        styleButton(updateStatusButton, FontAwesomeSolid.CHECK_CIRCLE, "btn", "btn-success");
        styleButton(viewDetailsButton, FontAwesomeSolid.EYE, "btn", "btn-info");
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
    private void onRefresh() {
        refresh();
    }

    private void refresh() {
        try {
            List<Order> orders = orderService.getOrdersForUser(Session.currentUserId());
            Status filter = selectedFilterStatus();
            if (filter != null) {
                orders = orders.stream()
                        .filter(order -> order.getStatus() == filter)
                        .collect(Collectors.toList());
            }

            orderTable.setItems(FXCollections.observableArrayList(orders));
            BigDecimal spend = orders.stream()
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            summaryLabel.setText(orders.size() + (orders.size() == 1 ? " order" : " orders")
                    + " - " + Money.format(spend) + " in total");
        } catch (SQLException e) {
            Notifier.error("Failed to load your orders", e);
        }
    }

    private Status selectedFilterStatus() {
        String selected = statusFilter.getValue();
        if (selected == null || ALL_STATUSES.equals(selected)) return null;

        for (Status status : Status.values()) {
            if (status.toString().equals(selected)) return status;
        }
        return null;
    }

    @FXML
    private void onUpdateStatus() {
        Order selected = orderTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Notifier.warn("Select an order first.");
            return;
        }
        Status newStatus = newStatusComboBox.getValue();
        if (newStatus == null) {
            Notifier.warn("Choose the status to apply.");
            return;
        }
        if (newStatus == selected.getStatus()) {
            Notifier.warn("Order #" + selected.getOrderId() + " is already " + newStatus + ".");
            return;
        }

        try {
            if (orderService.updateStatus(selected.getOrderId(), newStatus)) {
                Notifier.info("Order #" + selected.getOrderId() + " is now " + newStatus + ".");
            } else {
                Notifier.warn("Order #" + selected.getOrderId() + " could not be found.");
            }
            refresh();
        } catch (SQLException e) {
            Notifier.error("Status update failed", e);
        }
    }

    @FXML
    private void onViewDetails() {
        Order selected = orderTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Notifier.warn("Select an order to view its items.");
            return;
        }

        ViewLoader.<OrderDetailController>openModal(
                rootPane.getScene().getWindow(),
                "order_detail.fxml",
                "Order #" + selected.getOrderId(),
                controller -> controller.loadOrder(selected));
    }
}
