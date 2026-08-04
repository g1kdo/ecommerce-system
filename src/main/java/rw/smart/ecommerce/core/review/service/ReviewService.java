package rw.smart.ecommerce.core.review.service;

import rw.smart.ecommerce.core.review.dao.ReviewContentDAO;
import rw.smart.ecommerce.core.review.dao.ReviewDAO;
import rw.smart.ecommerce.core.review.model.Review;
import rw.smart.ecommerce.core.review.model.ReviewContent;
import rw.smart.ecommerce.utils.MongoConnection;
import rw.smart.ecommerce.utils.exceptions.DocumentStoreException;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Business logic for reviews, and the seam where the hybrid model is joined.
 *
 * <p>The <b>relational</b> half ({@link ReviewDAO}) owns the rating: it needs
 * referential integrity to a real product and user, the one-rating-per-user
 * constraint, and SQL {@code AVG()} for a product's star rating.</p>
 *
 * <p>The <b>document</b> half ({@link ReviewContentDAO}) owns the variable-shape
 * content: body text, photos, helpful votes, seller responses, edit history. The
 * two are joined on {@code review_id}.</p>
 *
 * <p>Write ordering is deliberate — the rating is committed first, so a document
 * store outage can never cost us a rating. If the content write then fails, a
 * {@link DocumentStoreException} says so precisely rather than implying the whole
 * submission was lost.</p>
 */
public class ReviewService {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;

    private final ReviewDAO reviewDAO;
    private final ReviewContentDAO contentDAO;

    public ReviewService() {
        this(new ReviewDAO(), new ReviewContentDAO());
    }

    public ReviewService(ReviewDAO reviewDAO, ReviewContentDAO contentDAO) {
        this.reviewDAO = reviewDAO;
        this.contentDAO = contentDAO;
    }

    /** Ratings for a product, newest first. */
    public List<Review> getReviews(int productId) throws SQLException {
        return reviewDAO.findByProduct(productId).stream()
                .sorted(Comparator.comparing(Review::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    /** Average rating rounded to one decimal; zero when a product has no ratings. */
    public BigDecimal getAverageRating(int productId) throws SQLException {
        return reviewDAO.getAverageRating(productId).setScale(1, RoundingMode.HALF_UP);
    }

    /** Records only the rating (relational half). */
    public int rateProduct(int productId, int userId, int rating) throws SQLException {
        validateRating(rating);
        return reviewDAO.upsertRating(productId, userId, rating);
    }

    /**
     * Records a rating and its document content together.
     *
     * @return the {@code review_id} shared by both halves
     * @throws SQLException            the rating could not be recorded — nothing was saved
     * @throws DocumentStoreException  the rating was saved but the content was not
     */
    public int submitReview(int productId, int userId, int rating, String body, List<String> tags)
            throws SQLException {
        int reviewId = rateProduct(productId, userId, rating);

        boolean hasContent = (body != null && !body.isBlank()) || (tags != null && !tags.isEmpty());
        if (!hasContent) return reviewId;

        ReviewContent content = new ReviewContent(reviewId, productId, body == null ? null : body.trim());
        content.setTags(tags);
        try {
            contentDAO.save(content);
        } catch (RuntimeException e) {
            throw new DocumentStoreException(
                    "Rating saved, but the review text could not be stored: " + e.getMessage(), e);
        }
        return reviewId;
    }

    /**
     * Document content for a product's reviews, keyed by {@code review_id}.
     * Returns an empty map when the store cannot be reached — callers pair this
     * with {@link #isContentStoreAvailable()} so the UI can say why content is
     * missing instead of silently showing ratings with no text.
     */
    public Map<Integer, ReviewContent> getReviewContent(int productId) {
        try {
            return contentDAO.findByProduct(productId);
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    public ReviewContent getReviewContent(Review review) {
        try {
            return contentDAO.findByReviewId(review.getReviewId());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Registers a helpful vote; false when the review has no content document yet. */
    public boolean markHelpful(int reviewId) {
        return contentDAO.markHelpful(reviewId);
    }

    public boolean isContentStoreAvailable() {
        return MongoConnection.isAvailable();
    }

    private void validateRating(int rating) {
        if (rating < MIN_RATING || rating > MAX_RATING)
            throw new InvalidInputException("Rating must be between " + MIN_RATING + " and " + MAX_RATING + ".");
    }
}
