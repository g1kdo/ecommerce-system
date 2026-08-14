package rw.smart.ecommerce.core.review.dto;

import rw.smart.ecommerce.core.review.model.Review;

import java.util.List;

public record ReviewResponse(
        String id,
        Long productId,
        Long userId,
        Integer rating,
        String title,
        String comment,
        List<String> tags,
        Integer helpfulVotes,
        String createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getProductId(),
                review.getUserId(),
                review.getRating(),
                review.getTitle(),
                review.getComment(),
                review.getTags() == null ? List.of() : List.copyOf(review.getTags()),
                review.getHelpfulVotes() == null ? 0 : review.getHelpfulVotes(),
                review.getCreatedAt() == null ? null : review.getCreatedAt().toString());
    }
}
