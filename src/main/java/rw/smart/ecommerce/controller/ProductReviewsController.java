package rw.smart.ecommerce.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import rw.smart.ecommerce.core.product.model.Product;
import rw.smart.ecommerce.core.review.model.Review;
import rw.smart.ecommerce.core.review.service.ReviewService;
import rw.smart.ecommerce.core.user.model.User;
import rw.smart.ecommerce.core.user.service.UserService;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;
import rw.smart.ecommerce.utils.session.Session;
import rw.smart.ecommerce.utils.ui.Notifier;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ratings for one product: the SQL average, every rating on record, and a form
 * to add or replace the signed-in user's own rating (one per user per product).
 */
public class ProductReviewsController {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private VBox reviewsRoot;
    @FXML private Label productLabel;
    @FXML private Label averageLabel;
    @FXML private TableView<Review> reviewTable;
    @FXML private TableColumn<Review, String> reviewerColumn;
    @FXML private TableColumn<Review, String> ratingColumn;
    @FXML private TableColumn<Review, String> dateColumn;
    @FXML private Spinner<Integer> ratingSpinner;
    @FXML private Button submitRatingButton;
    @FXML private Button closeButton;

    private final ReviewService reviewService = new ReviewService();
    private final UserService userService = new UserService();
    private final Map<Integer, String> reviewerNamesById = new HashMap<>();

    private Product product;

    @FXML
    public void initialize() {
        reviewsRoot.getStyleClass().add("form-shell");
        reviewTable.getStyleClass().add("product-table");
        reviewTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        reviewTable.setPlaceholder(new Label("No ratings yet - be the first."));

        ratingSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5, 5));
        submitRatingButton.getStyleClass().addAll("btn", "btn-success");
        submitRatingButton.setGraphic(new FontIcon(FontAwesomeSolid.STAR));
        submitRatingButton.setContentDisplay(ContentDisplay.LEFT);
        closeButton.getStyleClass().addAll("btn", "btn-default");
        closeButton.setGraphic(new FontIcon(FontAwesomeSolid.TIMES));
        closeButton.setContentDisplay(ContentDisplay.LEFT);

        reviewerColumn.setCellValueFactory(data ->
                new SimpleStringProperty(reviewerNamesById.getOrDefault(
                        data.getValue().getUserId(), "User #" + data.getValue().getUserId())));
        ratingColumn.setCellValueFactory(data -> new SimpleStringProperty(stars(data.getValue().getRating())));
        dateColumn.setCellValueFactory(data -> {
            var createdAt = data.getValue().getCreatedAt();
            return new SimpleStringProperty(createdAt == null ? "" : createdAt.format(TIMESTAMP_FORMAT));
        });
    }

    /** Called by the opening screen before the dialog is shown. */
    public void loadProduct(Product product) {
        this.product = product;
        productLabel.setText(product.getName());
        refresh();
    }

    private void refresh() {
        try {
            List<Review> reviews = reviewService.getReviews(product.getProductId());
            resolveReviewerNames(reviews);
            reviewTable.setItems(FXCollections.observableArrayList(reviews));

            BigDecimal average = reviewService.getAverageRating(product.getProductId());
            averageLabel.setText(reviews.isEmpty()
                    ? "No ratings yet"
                    : "Average " + average + " / 5 from " + reviews.size() + (reviews.size() == 1 ? " rating" : " ratings"));

            // preselect the user's existing rating so re-rating starts from it
            reviews.stream()
                    .filter(review -> review.getUserId() == Session.currentUserId())
                    .findFirst()
                    .ifPresent(review -> ratingSpinner.getValueFactory().setValue(review.getRating()));
        } catch (SQLException e) {
            Notifier.error("Failed to load reviews", e);
        }
    }

    /** Reviews only carry a user_id; look up each distinct reviewer once. */
    private void resolveReviewerNames(List<Review> reviews) throws SQLException {
        for (Review review : reviews) {
            if (reviewerNamesById.containsKey(review.getUserId())) {
                continue;
            }
            User reviewer = userService.getUser(review.getUserId());
            if (reviewer != null) {
                reviewerNamesById.put(review.getUserId(), reviewer.getFullName());
            }
        }
    }

    @FXML
    private void onSubmitRating() {
        try {
            reviewService.rateProduct(product.getProductId(), Session.currentUserId(), ratingSpinner.getValue());
            Notifier.info("Thanks for rating " + product.getName() + ".");
            refresh();
        } catch (InvalidInputException e) {
            Notifier.warn(e.getMessage());
        } catch (SQLException e) {
            Notifier.error("Could not save your rating", e);
        }
    }

    @FXML
    private void onClose() {
        ((Stage) reviewsRoot.getScene().getWindow()).close();
    }

    private String stars(int rating) {
        return "*".repeat(rating) + " (" + rating + "/5)";
    }
}
