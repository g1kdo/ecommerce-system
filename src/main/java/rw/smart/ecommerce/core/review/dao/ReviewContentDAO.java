package rw.smart.ecommerce.core.review.dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import rw.smart.ecommerce.core.review.model.ReviewContent;
import rw.smart.ecommerce.utils.MongoConnection;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.mongodb.client.model.Filters.eq;

/**
 * Document-store access for review content — the NoSQL counterpart to
 * {@link ReviewDAO}. Queries are by {@code review_id} / {@code product_id}, which
 * is how the relational half joins to it.
 *
 * The collection is supplied rather than looked up directly so the store can be
 * substituted in tests without a running server.
 */
public class ReviewContentDAO {

    private final Supplier<MongoCollection<Document>> collection;

    public ReviewContentDAO() {
        this(() -> MongoConnection.collection(MongoConnection.REVIEW_CONTENT_COLLECTION));
    }

    public ReviewContentDAO(Supplier<MongoCollection<Document>> collection) {
        this.collection = collection;
    }

    public ReviewContent findByReviewId(int reviewId) {
        Document document = collection.get().find(eq(ReviewContentMapper.REVIEW_ID, reviewId)).first();
        return document == null ? null : ReviewContentMapper.fromDocument(document);
    }

    /** All content for a product's reviews, keyed by {@code review_id}. */
    public Map<Integer, ReviewContent> findByProduct(int productId) {
        Map<Integer, ReviewContent> byReviewId = new HashMap<>();
        for (Document document : collection.get().find(eq(ReviewContentMapper.PRODUCT_ID, productId))) {
            ReviewContent content = ReviewContentMapper.fromDocument(document);
            byReviewId.put(content.getReviewId(), content);
        }
        return byReviewId;
    }

    /**
     * Writes content for a review, creating the document on first save. An edit to
     * an existing body is appended to {@code edit_history}, and votes / seller
     * response are preserved rather than clobbered — see
     * {@link ReviewContent#revisionOf}.
     */
    public void save(ReviewContent content) {
        ReviewContent previous = findByReviewId(content.getReviewId());
        ReviewContent revision = content.revisionOf(previous, Instant.now());

        collection.get().replaceOne(
                eq(ReviewContentMapper.REVIEW_ID, content.getReviewId()),
                ReviewContentMapper.toDocument(revision),
                new ReplaceOptions().upsert(true));
    }

    /** Atomic `$inc` so concurrent votes cannot overwrite each other. */
    public boolean markHelpful(int reviewId) {
        return collection.get().updateOne(
                eq(ReviewContentMapper.REVIEW_ID, reviewId),
                Updates.inc(ReviewContentMapper.HELPFUL_VOTES, 1)).getModifiedCount() > 0;
    }
}
