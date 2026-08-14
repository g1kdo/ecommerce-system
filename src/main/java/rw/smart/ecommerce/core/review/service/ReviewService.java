package rw.smart.ecommerce.core.review.service;

import rw.smart.ecommerce.core.review.dto.ReviewRequest;
import rw.smart.ecommerce.core.review.dto.ReviewResponse;
import rw.smart.ecommerce.core.review.dto.ReviewSummaryResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ReviewService {

    ReviewResponse create(ReviewRequest request);

    List<ReviewResponse> findByProduct(Long productId, Integer page, Integer size);

    ReviewSummaryResponse summarize(Long productId);

    /**
     * Summaries for many products in one call, backing the
     * {@code Product.reviewSummary} batch loader. Every requested id appears in
     * the result, including products with no reviews at all.
     */
    Map<Long, ReviewSummaryResponse> summarizeAll(Collection<Long> productIds);

    ReviewResponse markHelpful(String id);

    void delete(String id);
}
