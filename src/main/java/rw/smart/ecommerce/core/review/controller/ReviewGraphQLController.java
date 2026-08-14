package rw.smart.ecommerce.core.review.controller;

import jakarta.validation.Valid;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import rw.smart.ecommerce.core.review.dto.ReviewRequest;
import rw.smart.ecommerce.core.review.dto.ReviewResponse;
import rw.smart.ecommerce.core.review.dto.ReviewSummaryResponse;
import rw.smart.ecommerce.core.review.service.ReviewService;

import java.util.List;

/** GraphQL entry points for reviews, served from the document store. */
@Controller
public class ReviewGraphQLController {

    private final ReviewService reviewService;

    public ReviewGraphQLController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @QueryMapping
    public List<ReviewResponse> reviewsByProduct(@Argument Long productId,
                                                 @Argument Integer page,
                                                 @Argument Integer size) {
        return reviewService.findByProduct(productId, page, size);
    }

    @QueryMapping
    public ReviewSummaryResponse reviewSummary(@Argument Long productId) {
        return reviewService.summarize(productId);
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    public ReviewResponse addReview(@Argument @Valid ReviewRequest input) {
        return reviewService.create(input);
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    public ReviewResponse markReviewHelpful(@Argument String id) {
        return reviewService.markHelpful(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public boolean deleteReview(@Argument String id) {
        reviewService.delete(id);
        return true;
    }
}
