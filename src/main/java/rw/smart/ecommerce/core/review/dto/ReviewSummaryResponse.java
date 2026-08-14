package rw.smart.ecommerce.core.review.dto;

/**
 * Result of the {@code $group} aggregation over a product's review documents —
 * the document-store equivalent of a SQL {@code AVG(rating)}.
 */
public record ReviewSummaryResponse(Long productId, long reviewCount, double averageRating) {

    public static ReviewSummaryResponse empty(Long productId) {
        return new ReviewSummaryResponse(productId, 0L, 0.0);
    }
}
