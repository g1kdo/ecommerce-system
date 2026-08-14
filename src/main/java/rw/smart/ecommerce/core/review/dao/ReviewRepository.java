package rw.smart.ecommerce.core.review.dao;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Repository;
import rw.smart.ecommerce.config.MongoSettings;
import rw.smart.ecommerce.core.review.model.Review;
import rw.smart.ecommerce.core.review.dto.ReviewSummaryResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Document-store access for reviews, written directly against
 * {@code mongodb-driver-sync}. This is the NoSQL counterpart to the JPA
 * repositories — same role in the architecture, different engine.
 */
@Slf4j
@Repository
public class ReviewRepository {

    private final MongoDatabase database;
    private final String collectionName;

    public ReviewRepository(MongoDatabase database, MongoSettings settings) {
        this.database = database;
        this.collectionName = settings.getReviewCollection();
    }

    private MongoCollection<Document> collection() {
        return database.getCollection(collectionName);
    }

    /**
     * Product-scoped reads are the dominant query, so the index is created at
     * startup. Failure is logged rather than thrown — the document store is
     * optional infrastructure and must not block the context from refreshing.
     */
    @PostConstruct
    void ensureIndexes() {
        try {
            collection().createIndex(
                    Indexes.descending(ReviewMapper.PRODUCT_ID, ReviewMapper.CREATED_AT));
            collection().createIndex(
                    Indexes.ascending(ReviewMapper.PRODUCT_ID, ReviewMapper.USER_ID),
                    new IndexOptions().unique(true).name("uk_review_product_user"));
        } catch (RuntimeException e) {
            log.warn("Could not create review indexes ({}); reviews will still work, unindexed.", e.getMessage());
        }
    }

    public Review insert(Review review) {
        Document document = ReviewMapper.toDocument(review);
        collection().insertOne(document);

        Object generatedId = document.get(ReviewMapper.ID);
        review.setId(generatedId == null ? null : generatedId.toString());
        return review;
    }

    public Optional<Review> findById(String id) {
        ObjectId objectId = ReviewMapper.toObjectId(id);
        if (objectId == null) return Optional.empty();

        Document document = collection().find(Filters.eq(ReviewMapper.ID, objectId)).first();
        return Optional.ofNullable(document).map(ReviewMapper::fromDocument);
    }

    /** Newest first, skip/limit paging — the driver equivalent of a Pageable. */
    public List<Review> findByProductId(Long productId, int page, int size) {
        FindIterable<Document> documents = collection()
                .find(Filters.eq(ReviewMapper.PRODUCT_ID, productId))
                .sort(Sorts.descending(ReviewMapper.CREATED_AT))
                .skip(Math.max(page, 0) * size)
                .limit(size);

        return read(documents);
    }

    public long countByProductId(Long productId) {
        return collection().countDocuments(Filters.eq(ReviewMapper.PRODUCT_ID, productId));
    }

    public boolean existsByProductIdAndUserId(Long productId, Long userId) {
        Bson filter = Filters.and(
                Filters.eq(ReviewMapper.PRODUCT_ID, productId),
                Filters.eq(ReviewMapper.USER_ID, userId));

        return collection().countDocuments(filter) > 0;
    }

    /**
     * Average rating via a {@code $group} pipeline — the document-store answer to
     * a SQL {@code AVG(rating)}, computed server-side rather than by pulling every
     * review across the wire.
     */
    public ReviewSummaryResponse summarize(Long productId) {
        List<Bson> pipeline = List.of(
                new Document("$match", new Document(ReviewMapper.PRODUCT_ID, productId)),
                new Document("$group", new Document("_id", "$" + ReviewMapper.PRODUCT_ID)
                        .append("reviewCount", new Document("$sum", 1))
                        .append("averageRating", new Document("$avg", "$" + ReviewMapper.RATING))));

        Document result = collection().aggregate(pipeline).first();
        if (result == null) return ReviewSummaryResponse.empty(productId);

        long count = result.get("reviewCount") instanceof Number n ? n.longValue() : 0L;
        double average = result.get("averageRating") instanceof Number n ? n.doubleValue() : 0.0;

        // Two decimals is what a product page displays; more is false precision.
        return new ReviewSummaryResponse(productId, count, Math.round(average * 100.0) / 100.0);
    }

    /** Atomic {@code $inc} so concurrent votes cannot overwrite each other. */
    public boolean incrementHelpfulVotes(String id) {
        ObjectId objectId = ReviewMapper.toObjectId(id);
        if (objectId == null) return false;

        UpdateResult result = collection().updateOne(
                Filters.eq(ReviewMapper.ID, objectId),
                Updates.inc(ReviewMapper.HELPFUL_VOTES, 1));

        return result.getModifiedCount() > 0;
    }

    public boolean deleteById(String id) {
        ObjectId objectId = ReviewMapper.toObjectId(id);
        if (objectId == null) return false;

        return collection().deleteOne(Filters.eq(ReviewMapper.ID, objectId)).getDeletedCount() > 0;
    }

    /** Called when a product is removed — documents have no cascading FK. */
    public long deleteByProductId(Long productId) {
        return collection().deleteMany(Filters.eq(ReviewMapper.PRODUCT_ID, productId)).getDeletedCount();
    }

    private List<Review> read(FindIterable<Document> documents) {
        List<Review> reviews = new ArrayList<>();
        for (Document document : documents) {
            reviews.add(ReviewMapper.fromDocument(document));
        }
        return reviews;
    }
}
