package rw.smart.ecommerce.core.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.smart.ecommerce.core.review.dto.ReviewRequest;
import rw.smart.ecommerce.core.review.dto.ReviewResponse;
import rw.smart.ecommerce.core.review.dto.ReviewSummaryResponse;
import rw.smart.ecommerce.core.review.service.ReviewService;
import rw.smart.ecommerce.utils.response.StandardResponse;

import java.util.List;

/**
 * Reviews live in MongoDB, so ids here are {@code ObjectId} hex strings rather
 * than the numeric ids used everywhere else.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reviews", description = "Product reviews and rating summaries, stored as documents")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "List the reviews for a product",
            description = "Newest first, paginated with `page` and `size` (capped at 100).")
    @GetMapping("/products/{productId}/reviews")
    public ResponseEntity<StandardResponse<List<ReviewResponse>>> findByProduct(
            @PathVariable Long productId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        List<ReviewResponse> reviews = reviewService.findByProduct(productId, page, size);
        return ResponseEntity.ok(StandardResponse.ok(reviews.size() + " review(s) retrieved", reviews));
    }

    @Operation(summary = "Get the rating summary for a product",
            description = """
                    Review count and average rating, computed by a MongoDB $group pipeline
                    and cached for five minutes - it is the most expensive read in the system.""")
    @GetMapping("/products/{productId}/reviews/summary")
    public ResponseEntity<StandardResponse<ReviewSummaryResponse>> summary(@PathVariable Long productId) {
        return ResponseEntity.ok(
                StandardResponse.ok("Review summary retrieved successfully", reviewService.summarize(productId)));
    }

    @Operation(summary = "Submit a review",
            description = "One review per user per product; a second attempt is rejected with 409.")
    @PostMapping("/reviews")
    public ResponseEntity<StandardResponse<ReviewResponse>> create(@Valid @RequestBody ReviewRequest request) {
        ReviewResponse created = reviewService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StandardResponse.created("Review submitted successfully", created));
    }

    @Operation(summary = "Mark a review as helpful",
            description = "Atomic $inc, so concurrent votes cannot overwrite one another.")
    @PatchMapping("/reviews/{id}/helpful")
    public ResponseEntity<StandardResponse<ReviewResponse>> markHelpful(@PathVariable String id) {
        return ResponseEntity.ok(StandardResponse.ok("Thanks for the feedback", reviewService.markHelpful(id)));
    }

    @Operation(summary = "Delete a review (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<StandardResponse<Void>> delete(@PathVariable String id) {
        reviewService.delete(id);
        return ResponseEntity.ok(StandardResponse.ok("Review deleted successfully", null));
    }
}
