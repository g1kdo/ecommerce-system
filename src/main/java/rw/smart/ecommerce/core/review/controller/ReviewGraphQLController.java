package rw.smart.ecommerce.core.review.controller;

import jakarta.validation.Valid;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import rw.smart.ecommerce.core.review.dto.ReviewRequest;
import rw.smart.ecommerce.core.review.dto.ReviewResponse;
import rw.smart.ecommerce.core.review.dto.ReviewSummaryResponse;
import rw.smart.ecommerce.core.product.dto.ProductResponse;
import rw.smart.ecommerce.core.review.service.ReviewService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Resolves {@code Product.reviewSummary} for a whole page of products at once.
     *
     * This is the N+1 that appears the moment a rating is shown on a catalogue
     * listing. GraphQL resolves fields per object, so a 20-product page asking for
     * star ratings would call {@code summarize(productId)} twenty times — twenty
     * separate MongoDB aggregations over the same collection, for one screen.
     *
     * {@code @BatchMapping} is Spring for GraphQL's wrapper over a DataLoader. The
     * engine collects every product in the selection set, invokes this method once
     * with all of them, and matches results back by key. Twenty aggregations
     * become one {@code $match: {productId: {$in: [...]}}}.
     *
     * The map is keyed by the source object, so {@code ProductResponse} has to have
     * value equality — it is a record, so it does.
     *
     * Note this field is <em>only</em> resolved when a client selects it. A query
     * for {@code id name price} pays nothing for it, which is the property that
     * makes putting it on the type safe in the first place.
     */
    @BatchMapping(typeName = "Product", field = "reviewSummary")
    public Map<ProductResponse, ReviewSummaryResponse> reviewSummary(List<ProductResponse> products) {
        List<Long> productIds = products.stream().map(ProductResponse::id).toList();
        Map<Long, ReviewSummaryResponse> byProductId = reviewService.summarizeAll(productIds);

        Map<ProductResponse, ReviewSummaryResponse> byProduct = new LinkedHashMap<>();
        for (ProductResponse product : products) {
            byProduct.put(product, byProductId.getOrDefault(
                    product.id(), ReviewSummaryResponse.empty(product.id())));
        }
        return byProduct;
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
