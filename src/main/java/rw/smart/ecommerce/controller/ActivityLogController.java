package rw.smart.ecommerce.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import rw.smart.ecommerce.core.log.enums.EventType;
import rw.smart.ecommerce.core.log.model.LogEntry;
import rw.smart.ecommerce.core.log.service.LogService;
import rw.smart.ecommerce.core.user.model.User;
import rw.smart.ecommerce.core.user.service.UserService;
import rw.smart.ecommerce.utils.session.Session;
import rw.smart.ecommerce.utils.ui.Notifier;
import rw.smart.ecommerce.utils.ui.RefreshableView;

import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read view over the log collection — the second document-store use case in the
 * hybrid model. Logs are never joined relationally, so this screen queries them
 * directly by recency, by user, or by event type.
 */
public class ActivityLogController implements RefreshableView {

    private static final String ALL_EVENTS = "All events";
    private static final int PAGE_LIMIT = 200;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML private BorderPane rootPane;
    @FXML private HBox toolbar;
    @FXML private Label storeStatusLabel;
    @FXML private ComboBox<String> eventFilter;
    @FXML private CheckBox mineOnlyCheckBox;
    @FXML private Label countLabel;
    @FXML private Button refreshButton;
    @FXML private TableView<LogEntry> logTable;
    @FXML private TableColumn<LogEntry, String> timeColumn;
    @FXML private TableColumn<LogEntry, String> eventColumn;
    @FXML private TableColumn<LogEntry, String> userColumn;
    @FXML private TableColumn<LogEntry, String> detailsColumn;

    private final LogService logService = new LogService();
    private final UserService userService = new UserService();
    private final Map<Integer, String> userNamesById = new HashMap<>();

    @FXML
    public void initialize() {
        configureUi();

        timeColumn.setCellValueFactory(data -> {
            var timestamp = data.getValue().getTimestamp();
            return new SimpleStringProperty(timestamp == null ? ""
                    : TIMESTAMP_FORMAT.format(timestamp.atZone(ZoneId.systemDefault())));
        });
        eventColumn.setCellValueFactory(data ->
                new SimpleStringProperty(EventType.labelOf(data.getValue().getEventType())));
        userColumn.setCellValueFactory(data -> new SimpleStringProperty(userLabel(data.getValue().getUserId())));
        detailsColumn.setCellValueFactory(data -> new SimpleStringProperty(formatDetails(data.getValue())));

        List<String> filterOptions = new ArrayList<>();
        filterOptions.add(ALL_EVENTS);
        for (EventType eventType : EventType.values()) {
            filterOptions.add(eventType.label());
        }
        eventFilter.setItems(FXCollections.observableArrayList(filterOptions));
        eventFilter.getSelectionModel().selectFirst();
        eventFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> refresh());
        mineOnlyCheckBox.selectedProperty().addListener((obs, wasSelected, isSelected) -> refresh());

        refresh();
    }

    private void configureUi() {
        rootPane.getStyleClass().add("content-pane");
        toolbar.getStyleClass().add("toolbar-panel");
        logTable.getStyleClass().add("product-table");
        logTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        logTable.setPlaceholder(new Label("No log events recorded yet."));

        refreshButton.getStyleClass().addAll("btn", "btn-default");
        refreshButton.setGraphic(new FontIcon(FontAwesomeSolid.SYNC_ALT));
        refreshButton.setContentDisplay(ContentDisplay.LEFT);

        Tooltip.install(eventFilter, new Tooltip("Filters with a query on event_type"));
        Tooltip.install(mineOnlyCheckBox, new Tooltip("Filters with a query on user_id"));
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
        if (!logService.isAvailable()) {
            showStoreUnavailable(true);
            logTable.setItems(FXCollections.observableArrayList());
            countLabel.setText("");
            return;
        }
        showStoreUnavailable(false);

        try {
            EventType selectedEvent = selectedEventType();
            boolean mineOnly = mineOnlyCheckBox.isSelected();

            List<LogEntry> entries;
            if (mineOnly) {
                entries = logService.getForUser(Session.currentUserId(), PAGE_LIMIT);
                if (selectedEvent != null) {
                    String eventName = selectedEvent.name();
                    entries = entries.stream()
                            .filter(entry -> eventName.equals(entry.getEventType()))
                            .collect(Collectors.toList());
                }
            } else if (selectedEvent != null) {
                entries = logService.getByEventType(selectedEvent, PAGE_LIMIT);
            } else {
                entries = logService.getRecent(PAGE_LIMIT);
            }

            logTable.setItems(FXCollections.observableArrayList(entries));
            countLabel.setText(entries.size() + (entries.size() == 1 ? " event" : " events")
                    + (entries.size() == PAGE_LIMIT ? " (most recent " + PAGE_LIMIT + ")" : ""));
        } catch (RuntimeException e) {
            showStoreUnavailable(true);
            Notifier.error("Failed to read the activity log", e);
        }
    }

    private void showStoreUnavailable(boolean unavailable) {
        storeStatusLabel.setText("Document store unreachable - the activity log lives in MongoDB "
                + "(mongo.uri in db.properties). Events raised while it is down are dropped, not queued.");
        storeStatusLabel.setVisible(unavailable);
        storeStatusLabel.setManaged(unavailable);
    }

    private EventType selectedEventType() {
        String selected = eventFilter.getValue();
        if (selected == null || ALL_EVENTS.equals(selected)) return null;

        for (EventType eventType : EventType.values()) {
            if (eventType.label().equals(selected)) return eventType;
        }
        return null;
    }

    /** Resolves each distinct user_id once; logs may reference users freely. */
    private String userLabel(Integer userId) {
        if (userId == null) return "(not signed in)";

        String cached = userNamesById.get(userId);
        if (cached != null) return cached;

        String label = "User #" + userId;
        try {
            User user = userService.getUser(userId);
            if (user != null) {
                label = user.getFullName();
            }
        } catch (SQLException e) {
            // fall back to the id; a log row is not worth failing the screen over
        }
        userNamesById.put(userId, label);
        return label;
    }

    private String formatDetails(LogEntry entry) {
        return entry.getDetails().entrySet().stream()
                .map(detail -> detail.getKey() + "=" + detail.getValue())
                .collect(Collectors.joining(", "));
    }
}
