package rw.smart.ecommerce.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import rw.smart.ecommerce.core.log.enums.EventType;
import rw.smart.ecommerce.core.log.service.LogService;
import rw.smart.ecommerce.core.product.model.Product;
import rw.smart.ecommerce.core.review.model.Review;
import rw.smart.ecommerce.core.review.model.ReviewContent;
import rw.smart.ecommerce.core.review.service.ReviewService;
import rw.smart.ecommerce.core.user.model.User;
import rw.smart.ecommerce.core.user.service.UserService;
import rw.smart.ecommerce.utils.exceptions.DocumentStoreException;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;
import rw.smart.ecommerce.utils.session.Session;
import rw.smart.ecommerce.utils.ui.Notifier;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reviews for one product, and the screen where the hybrid model is visible:
 * ratings and the SQL average come from the relational half, while the comment,
 * photos, tags, helpful votes, seller response and edit history come from the
 * document half. When the document store is unreachable the ratings still show and
 * a banner explains what is missing.
 */
public class ProductReviewsController {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int COMMENT_PREVIEW_LENGTH = 60;

    @FXML private VBox reviewsRoot;
    @FXML private VBox detailPane;
    @FXML private Label productLabel;
    @FXML private Label averageLabel;
    @FXML private Label storeStatusLabel;
    @FXML private TableView<Review> reviewTable;
    @FXML private TableColumn<Review, String> reviewerColumn;
    @FXML private TableColumn<Review, String> ratingColumn;
    @FXML private TableColumn<Review, String> commentColumn;
    @FXML private TableColumn<Review, String> helpfulColumn;
    @FXML private TableColumn<Review, String> dateColumn;
    @FXML private Label bodyLabel;
    @FXML private Label photosLabel;
    @FXML private Label tagsLabel;
    @FXML private Label sellerResponseLabel;
    @FXML private Label editHistoryLabel;
    @FXML private Button markHelpfulButton;
    @FXML private Spinner<Integer> ratingSpinner;
    @FXML private TextField tagsField;
    @FXML private TextArea bodyField;
    @FXML private Button submitRatingButton;
    @FXML private Button closeButton;

    private final ReviewService reviewService = new ReviewService();
    private final UserService userService = new UserService();
    private final LogService logService = new LogService();

    private final Map<Integer, String> reviewerNamesById = new HashMap<>();
    private final Map<Integer, ReviewContent> contentByReviewId = new HashMap<>();

    private Product product;
    private boolean contentStoreAvailable;

    @FXML
    public void initialize() {
        configureUi();

        reviewerColumn.setCellValueFactory(data ->
                new SimpleStringProperty(reviewerNamesById.getOrDefault(
                        data.getValue().getUserId(), "User #" + data.getValue().getUserId())));
        ratingColumn.setCellValueFactory(data -> new SimpleStringProperty(stars(data.getValue().getRating())));
        commentColumn.setCellValueFactory(data -> new SimpleStringProperty(commentPreview(data.getValue())));
        helpfulColumn.setCellValueFactory(data -> {
            ReviewContent content = contentByReviewId.get(data.getValue().getReviewId());
            return new SimpleStringProperty(content == null ? "-" : String.valueOf(content.getHelpfulVotes()));
        });
        dateColumn.setCellValueFactory(data -> {
            var createdAt = data.getValue().getCreatedAt();
            return new SimpleStringProperty(createdAt == null ? "" : createdAt.format(TIMESTAMP_FORMAT));
        });

        reviewTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldReview, newReview) -> showDetail(newReview));
    }

    private void configureUi() {
        reviewsRoot.getStyleClass().add("form-shell");
        detailPane.getStyleClass().add("detail-pane");
        reviewTable.getStyleClass().add("product-table");
        reviewTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        reviewTable.setPlaceholder(new Label("No ratings yet - be the first."));

        ratingSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5, 5));

        styleButton(submitRatingButton, FontAwesomeSolid.STAR, "btn", "btn-success");
        styleButton(markHelpfulButton, FontAwesomeSolid.THUMBS_UP, "btn", "btn-default");
        styleButton(closeButton, FontAwesomeSolid.TIMES, "btn", "btn-default");

        Tooltip.install(tagsField, new Tooltip("Free-form tags, stored with the review document"));
        Tooltip.install(bodyField, new Tooltip("Review text lives in the document store, not in SQL"));
    }

    private void styleButton(Button button, FontAwesomeSolid icon, String... styleClasses) {
        button.getStyleClass().addAll(styleClasses);
        button.setGraphic(new FontIcon(icon));
        button.setContentDisplay(ContentDisplay.LEFT);
    }

    /** Called by the opening screen before the dialog is shown. */
    public void loadProduct(Product product) {
        this.product = product;
        productLabel.setText(product.getName());
        refresh();
    }

    private void refresh() {
        contentStoreAvailable = reviewService.isContentStoreAvailable();
        updateStoreBanner();

        Review previouslySelected = reviewTable.getSelectionModel().getSelectedItem();
        try {
            List<Review> reviews = reviewService.getReviews(product.getProductId());
            resolveReviewerNames(reviews);

            contentByReviewId.clear();
            if (contentStoreAvailable) {
                contentByReviewId.putAll(reviewService.getReviewContent(product.getProductId()));
            }

            reviewTable.setItems(FXCollections.observableArrayList(reviews));

            BigDecimal average = reviewService.getAverageRating(product.getProductId());
            averageLabel.setText(reviews.isEmpty()
                    ? "No ratings yet"
                    : "Average " + average + " / 5 from " + reviews.size()
                            + (reviews.size() == 1 ? " rating" : " ratings"));

            // preselect the user's existing rating so re-rating starts from it
            reviews.stream()
                    .filter(review -> review.getUserId() == Session.currentUserId())
                    .findFirst()
                    .ifPresent(review -> ratingSpinner.getValueFactory().setValue(review.getRating()));

            restoreSelection(previouslySelected, reviews);
        } catch (SQLException e) {
            Notifier.error("Failed to load reviews", e);
        }
    }

    /**
     * Reloading replaces the row objects, so selection is restored by review_id.
     * Falling back to the first row means the detail pane shows real content on
     * open instead of an empty placeholder.
     */
    private void restoreSelection(Review previouslySelected, List<Review> reviews) {
        if (reviews.isEmpty()) {
            showDetail(null);
            return;
        }

        Review toSelect = reviews.get(0);
        if (previouslySelected != null) {
            toSelect = reviews.stream()
                    .filter(review -> review.getReviewId() == previouslySelected.getReviewId())
                    .findFirst()
                    .orElse(toSelect);
        }
        reviewTable.getSelectionModel().select(toSelect);
        showDetail(toSelect);
    }

    private void updateStoreBanner() {
        boolean show = !contentStoreAvailable;
        storeStatusLabel.setText("Document store unreachable - showing ratings only. "
                + "Review text, photos, tags and helpful votes are stored in MongoDB.");
        storeStatusLabel.setVisible(show);
        storeStatusLabel.setManaged(show);
        markHelpfulButton.setDisable(show);
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

    private void showDetail(Review review) {
        if (review == null) {
            bodyLabel.setText("Select a review to see its full content.");
            photosLabel.setText("");
            tagsLabel.setText("");
            sellerResponseLabel.setText("");
            editHistoryLabel.setText("");
            return;
        }

        ReviewContent content = contentByReviewId.get(review.getReviewId());
        if (content == null) {
            bodyLabel.setText(contentStoreAvailable
                    ? "This rating has no written review."
                    : "Review text is unavailable while the document store is unreachable.");
            photosLabel.setText("");
            tagsLabel.setText("");
            sellerResponseLabel.setText("");
            editHistoryLabel.setText("");
            return;
        }

        bodyLabel.setText(content.getBody() == null || content.getBody().isBlank()
                ? "(no review text)" : content.getBody());
        photosLabel.setText(content.getPhotos().isEmpty()
                ? "" : content.getPhotos().size() + " photo(s): " + String.join(", ", content.getPhotos()));
        tagsLabel.setText(content.getTags().isEmpty() ? "" : "Tags: " + String.join(", ", content.getTags()));
        sellerResponseLabel.setText(content.getSellerResponse() == null || content.getSellerResponse().isBlank()
                ? "" : "Seller response: " + content.getSellerResponse());
        editHistoryLabel.setText(content.getEditHistory().isEmpty()
                ? "" : "Edited " + content.getEditHistory().size() + " time(s); "
                        + content.getHelpfulVotes() + " found this helpful");
    }

    @FXML
    private void onSubmitRating() {
        int rating = ratingSpinner.getValue();
        String body = bodyField.getText();
        List<String> tags = parseTags(tagsField.getText());

        try {
            reviewService.submitReview(product.getProductId(), Session.currentUserId(), rating, body, tags);
            logService.log(EventType.REVIEW_SUBMITTED, Map.of(
                    "product_id", product.getProductId(),
                    "rating", rating,
                    "has_body", body != null && !body.isBlank()));
            Notifier.info("Thanks for reviewing " + product.getName() + ".");
            bodyField.clear();
            tagsField.clear();
            refresh();
        } catch (InvalidInputException e) {
            Notifier.warn(e.getMessage());
        } catch (DocumentStoreException e) {
            // the relational rating did land, so say exactly that
            Notifier.warn(e.getMessage());
            refresh();
        } catch (SQLException e) {
            Notifier.error("Could not save your review", e);
        }
    }

    @FXML
    private void onMarkHelpful() {
        Review selected = reviewTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Notifier.warn("Select a review to mark as helpful.");
            return;
        }

        try {
            if (!reviewService.markHelpful(selected.getReviewId())) {
                Notifier.warn("This rating has no written review to vote on.");
                return;
            }
            logService.log(EventType.REVIEW_MARKED_HELPFUL, Map.of("review_id", selected.getReviewId()));
            refresh(); // keeps the voted-on row selected, by review_id
        } catch (RuntimeException e) {
            Notifier.warn("Could not register the vote - document store unreachable.");
        }
    }

    @FXML
    private void onClose() {
        ((Stage) reviewsRoot.getScene().getWindow()).close();
    }

    private List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) return List.of();

        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toList());
    }

    private String commentPreview(Review review) {
        ReviewContent content = contentByReviewId.get(review.getReviewId());
        if (content == null || content.getBody() == null || content.getBody().isBlank()) return "";

        String body = content.getBody().replaceAll("\\s+", " ").trim();
        return body.length() <= COMMENT_PREVIEW_LENGTH ? body
                : body.substring(0, COMMENT_PREVIEW_LENGTH - 3) + "...";
    }

    private String stars(int rating) {
        return "*".repeat(rating) + " (" + rating + "/5)";
    }
}
