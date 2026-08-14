package rw.smart.ecommerce.core.review.dao;

import org.bson.Document;
import org.bson.types.ObjectId;
import rw.smart.ecommerce.core.review.model.Review;
import rw.smart.ecommerce.utils.BsonValues;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Translates between {@link Review} and its BSON document. Reads are lenient —
 * a schema-loose collection can hold dates as BSON dates or as ISO-8601 strings
 * (hand-written seed data), and both must load.
 */
public final class ReviewMapper {

    public static final String ID = "_id";
    public static final String PRODUCT_ID = "productId";
    public static final String USER_ID = "userId";
    public static final String RATING = "rating";
    public static final String TITLE = "title";
    public static final String COMMENT = "comment";
    public static final String TAGS = "tags";
    public static final String PHOTOS = "photos";
    public static final String HELPFUL_VOTES = "helpfulVotes";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";

    private ReviewMapper() {
        // utility class, no instances
    }

    /** The {@code _id} is left out so the server assigns one on insert. */
    public static Document toDocument(Review review) {
        return new Document()
                .append(PRODUCT_ID, review.getProductId())
                .append(USER_ID, review.getUserId())
                .append(RATING, review.getRating())
                .append(TITLE, review.getTitle())
                .append(COMMENT, review.getComment())
                .append(TAGS, review.getTags() == null ? List.of() : review.getTags())
                .append(PHOTOS, review.getPhotos() == null ? List.of() : review.getPhotos())
                .append(HELPFUL_VOTES, review.getHelpfulVotes() == null ? 0 : review.getHelpfulVotes())
                .append(CREATED_AT, toDate(review.getCreatedAt()))
                .append(UPDATED_AT, toDate(review.getUpdatedAt()));
    }

    public static Review fromDocument(Document document) {
        Review review = new Review();
        Object id = document.get(ID);
        review.setId(id == null ? null : id.toString());
        review.setProductId(BsonValues.readNullableLong(document.get(PRODUCT_ID)));
        review.setUserId(BsonValues.readNullableLong(document.get(USER_ID)));
        review.setRating(BsonValues.readNullableInt(document.get(RATING)));
        review.setTitle(document.getString(TITLE));
        review.setComment(document.getString(COMMENT));
        review.setTags(readStrings(document.get(TAGS)));
        review.setPhotos(readStrings(document.get(PHOTOS)));
        review.setHelpfulVotes(BsonValues.readInt(document.get(HELPFUL_VOTES)));
        review.setCreatedAt(BsonValues.readInstant(document.get(CREATED_AT)));
        review.setUpdatedAt(BsonValues.readInstant(document.get(UPDATED_AT)));
        return review;
    }

    public static ObjectId toObjectId(String id) {
        return ObjectId.isValid(id) ? new ObjectId(id) : null;
    }

    private static Date toDate(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

    private static List<String> readStrings(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object element : list) {
                if (element != null) values.add(element.toString());
            }
        }
        return values;
    }
}
