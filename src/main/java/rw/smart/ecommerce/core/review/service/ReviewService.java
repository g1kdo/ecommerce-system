package rw.smart.ecommerce.core.review.service;

import rw.smart.ecommerce.core.review.dao.ReviewDAO;
import rw.smart.ecommerce.core.review.model.Review;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business logic layer for product ratings. The relational side only stores the
 * 1-5 rating (see Review's javadoc); free-form review bodies live in the NoSQL
 * document store and are out of scope for this screen.
 */
public class ReviewService {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;

    private final ReviewDAO reviewDAO = new ReviewDAO();

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

    public int rateProduct(int productId, int userId, int rating) throws SQLException {
        if (rating < MIN_RATING || rating > MAX_RATING)
            throw new InvalidInputException("Rating must be between " + MIN_RATING + " and " + MAX_RATING + ".");

        return reviewDAO.upsertRating(productId, userId, rating);
    }
}
