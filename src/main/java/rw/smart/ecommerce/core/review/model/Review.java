package rw.smart.ecommerce.core.review.model;

import java.time.LocalDateTime;

/**
 * Relational shell for a review — only the fields that need referential
 * integrity and SQL aggregation (AVG rating). Free-form review content
 * (body text, photos, votes) is stored in the NoSQL document store —
 * see docs/nosql/reviews_schema.json.
 */
public class Review {

    private int reviewId;
    private int productId;
    private int userId;
    private int rating;
    private LocalDateTime createdAt;

    public Review() {

    }

    public Review(int reviewId, int productId, int userId, int rating, LocalDateTime createdAt) {
        this.reviewId = reviewId;
        this.productId = productId;
        this.userId = userId;
        this.rating = rating;
        this.createdAt = createdAt;
    }

    public int getReviewId() {
        return reviewId;
    }

    public void setReviewId(int reviewId) {
        this.reviewId = reviewId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
