package rw.smart.ecommerce.core.review.service;

import rw.smart.ecommerce.core.review.dto.ReviewRequest;
import rw.smart.ecommerce.core.review.dto.ReviewResponse;
import rw.smart.ecommerce.core.review.dto.ReviewSummaryResponse;

import java.util.List;

public interface ReviewService {

    ReviewResponse create(ReviewRequest request);

    List<ReviewResponse> findByProduct(Long productId, Integer page, Integer size);

    ReviewSummaryResponse summarize(Long productId);

    ReviewResponse markHelpful(String id);

    void delete(String id);
}
