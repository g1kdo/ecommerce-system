package rw.smart.ecommerce.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import rw.smart.ecommerce.core.category.model.Category;
import rw.smart.ecommerce.core.category.service.CategoryService;
import rw.smart.ecommerce.core.log.enums.EventType;
import rw.smart.ecommerce.core.log.service.LogService;
import rw.smart.ecommerce.utils.ui.Notifier;

import java.sql.SQLException;
import java.util.Map;

/** Create/edit dialog for a single category. */
public class CategoryFormController {

    private static final String UNIQUE_VIOLATION = "23505";

    @FXML private VBox formRoot;
    @FXML private Label titleLabel;
    @FXML private TextField nameField;
    @FXML private TextArea descriptionField;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private final CategoryService categoryService = new CategoryService();
    private final LogService logService = new LogService();

    private Category editingCategory; // null = create mode, non-null = edit mode

    @FXML
    public void initialize() {
        formRoot.getStyleClass().add("form-shell");
        saveButton.getStyleClass().addAll("btn", "btn-success");
        cancelButton.getStyleClass().addAll("btn", "btn-default");
        saveButton.setGraphic(new FontIcon(FontAwesomeSolid.SAVE));
        cancelButton.setGraphic(new FontIcon(FontAwesomeSolid.TIMES));
        saveButton.setContentDisplay(ContentDisplay.LEFT);
        cancelButton.setContentDisplay(ContentDisplay.LEFT);
        saveButton.setDefaultButton(true);

        Tooltip.install(nameField, new Tooltip("Category names must be unique"));
    }

    /** Called by CategoryListController to switch into edit mode. */
    public void loadCategory(Category category) {
        this.editingCategory = category;
        titleLabel.setText("Edit Category");
        nameField.setText(category.getName());
        descriptionField.setText(category.getDescription());
    }

    @FXML
    private void onSave() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            Notifier.warn("Name is required.");
            return;
        }

        String description = descriptionField.getText() == null ? null : descriptionField.getText().trim();
        Category category = editingCategory == null ? new Category() : editingCategory;
        category.setName(name);
        category.setDescription(description == null || description.isBlank() ? null : description);

        try {
            if (editingCategory == null) {
                int categoryId = categoryService.createCategory(category);
                logService.log(EventType.CATEGORY_CREATED, Map.of("category_id", categoryId, "name", name));
            } else {
                categoryService.updateCategory(category);
                logService.log(EventType.CATEGORY_UPDATED,
                        Map.of("category_id", category.getCategoryId(), "name", name));
            }
            Notifier.info("Category saved successfully.");
            closeWindow();
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                Notifier.warn("A category with that name already exists.");
            } else {
                Notifier.error("Save failed", e);
            }
        }
    }

    @FXML
    private void onCancel() {
        closeWindow();
    }

    private void closeWindow() {
        ((Stage) formRoot.getScene().getWindow()).close();
    }
}
