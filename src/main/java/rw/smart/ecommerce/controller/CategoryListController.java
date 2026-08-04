package rw.smart.ecommerce.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import rw.smart.ecommerce.core.category.model.Category;
import rw.smart.ecommerce.core.category.service.CategoryService;
import rw.smart.ecommerce.utils.ui.Notifier;
import rw.smart.ecommerce.utils.ui.RefreshableView;
import rw.smart.ecommerce.utils.ui.ViewLoader;

import java.sql.SQLException;
import java.util.List;

/**
 * CRUD screen over CategoryService. No JDBC here — the controller only talks to
 * the service layer, same as the product screens.
 */
public class CategoryListController implements RefreshableView {

    private static final String FOREIGN_KEY_VIOLATION = "23503";

    @FXML private BorderPane rootPane;
    @FXML private HBox toolbar;
    @FXML private TableView<Category> categoryTable;
    @FXML private TableColumn<Category, String> nameColumn;
    @FXML private TableColumn<Category, String> descriptionColumn;
    @FXML private Label countLabel;
    @FXML private Button newCategoryButton;
    @FXML private Button editCategoryButton;
    @FXML private Button deleteCategoryButton;
    @FXML private Button refreshButton;

    private final CategoryService categoryService = new CategoryService();

    @FXML
    public void initialize() {
        configureUi();

        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        descriptionColumn.setCellValueFactory(data -> {
            String description = data.getValue().getDescription();
            return new SimpleStringProperty(description == null ? "" : description);
        });

        categoryTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                onEditCategory();
            }
        });

        refresh();
    }

    private void configureUi() {
        rootPane.getStyleClass().add("content-pane");
        toolbar.getStyleClass().add("toolbar-panel");
        categoryTable.getStyleClass().add("product-table");
        categoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        categoryTable.setPlaceholder(new Label("No categories yet - create the first one."));

        styleButton(newCategoryButton, FontAwesomeSolid.PLUS, "btn", "btn-success");
        styleButton(editCategoryButton, FontAwesomeSolid.EDIT, "btn", "btn-info");
        styleButton(deleteCategoryButton, FontAwesomeSolid.TRASH, "btn", "btn-danger");
        styleButton(refreshButton, FontAwesomeSolid.SYNC_ALT, "btn", "btn-default");
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
            List<Category> categories = categoryService.getAllCategories();
            categoryTable.setItems(FXCollections.observableArrayList(categories));
            countLabel.setText(categories.size() + (categories.size() == 1 ? " category" : " categories"));
        } catch (SQLException e) {
            Notifier.error("Failed to load categories", e);
        }
    }

    @FXML
    private void onNewCategory() {
        openForm(null);
    }

    @FXML
    private void onEditCategory() {
        Category selected = categoryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Notifier.warn("Select a category to edit.");
            return;
        }
        openForm(selected);
    }

    @FXML
    private void onDeleteCategory() {
        Category selected = categoryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Notifier.warn("Select a category to delete.");
            return;
        }
        if (!Notifier.confirm("Delete " + selected.getName() + "?", "This action cannot be undone.")) {
            return;
        }

        try {
            categoryService.deleteCategory(selected.getCategoryId());
            Notifier.info("Category deleted successfully.");
            refresh();
        } catch (SQLException e) {
            if (FOREIGN_KEY_VIOLATION.equals(e.getSQLState())) {
                Notifier.warn("This category still has products. Move or delete them first.");
            } else {
                Notifier.error("Delete failed", e);
            }
        }
    }

    private void openForm(Category category) {
        ViewLoader.<CategoryFormController>openModal(
                rootPane.getScene().getWindow(),
                "category_form.fxml",
                category == null ? "New Category" : "Edit Category",
                controller -> {
                    if (category != null) {
                        controller.loadCategory(category);
                    }
                });
        refresh();
    }
}
