package rw.smart.ecommerce.core.review.service.impl;

import com.mongodb.MongoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.smart.ecommerce.config.CacheConfig;
import rw.smart.ecommerce.core.review.model.Review;
import rw.smart.ecommerce.core.review.dto.ReviewRequest;
import rw.smart.ecommerce.core.review.dto.ReviewResponse;
import rw.smart.ecommerce.core.review.dto.ReviewSummaryResponse;
import rw.smart.ecommerce.core.product.dao.ProductRepository;
import rw.smart.ecommerce.core.user.dao.UserRepository;
import rw.smart.ecommerce.core.review.dao.ReviewRepository;
import rw.smart.ecommerce.core.review.service.ReviewService;
import rw.smart.ecommerce.utils.exceptions.DocumentStoreException;
import rw.smart.ecommerce.utils.exceptions.DuplicateResourceException;
import rw.smart.ecommerce.utils.exceptions.ResourceNotFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reviews straddle both stores: the identifiers are validated against
 * PostgreSQL, the review itself is written as a document.
 *
 * {@code @Transactional(readOnly = true)} covers only the relational reads —
 * the document write is outside any transaction, which is exactly why the
 * validation happens first and the failure mode is reported explicitly.
 */
@Slf4j
@Service
public class ReviewServiceImpl implements ReviewService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ReviewServiceImpl(ReviewRepository reviewRepository,
                             ProductRepository productRepository,
                             UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Override
    @CacheEvict(value = CacheConfig.REVIEW_SUMMARIES, key = "#request.productId()")
    @Transactional(readOnly = true)
    public ReviewResponse create(ReviewRequest request) {
        // Documents carry no foreign keys, so referential integrity is checked
        // here instead of by the database.
        if (!productRepository.existsById(request.productId()))
            throw ResourceNotFoundException.of("Product", request.productId());

        if (!userRepository.existsById(request.userId()))
            throw ResourceNotFoundException.of("User", request.userId());

        if (reviewRepository.existsByProductIdAndUserId(request.productId(), request.userId()))
            throw new DuplicateResourceException("You have already reviewed this product.");

        Review review = new Review();
        review.setProductId(request.productId());
        review.setUserId(request.userId());
        review.setRating(request.rating());
        review.setTitle(request.title());
        review.setComment(request.comment());
        review.setTags(request.tags() == null ? new ArrayList<>() : new ArrayList<>(request.tags()));
        review.setHelpfulVotes(0);
        review.setCreatedAt(Instant.now());
        review.setUpdatedAt(review.getCreatedAt());

        try {
            return ReviewResponse.from(reviewRepository.insert(review));
        } catch (MongoException e) {
            throw new DocumentStoreException("The review could not be saved to the document store.", e);
        }
    }

    @Override
    public List<ReviewResponse> findByProduct(Long productId, Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, MAX_PAGE_SIZE);

        return reviewRepository.findByProductId(productId, pageNumber, pageSize).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    @Override
    @Cacheable(value = CacheConfig.REVIEW_SUMMARIES, key = "#productId")
    public ReviewSummaryResponse summarize(Long productId) {
        return reviewRepository.summarize(productId);
    }

    @Override
    public ReviewResponse markHelpful(String id) {
        if (!reviewRepository.incrementHelpfulVotes(id))
            throw new ResourceNotFoundException("Review not found with id " + id);

        return reviewRepository.findById(id)
                .map(ReviewResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id " + id));
    }

    @Override
    // The document is gone before its productId can be read, so the whole
    // cache is cleared rather than guessing which summary went stale.
    @CacheEvict(value = CacheConfig.REVIEW_SUMMARIES, allEntries = true)
    public void delete(String id) {
        if (!reviewRepository.deleteById(id))
            throw new ResourceNotFoundException("Review not found with id " + id);

        log.debug("Deleted review document {}", id);
    }
}
