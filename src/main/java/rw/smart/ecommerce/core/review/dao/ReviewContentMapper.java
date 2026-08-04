package rw.smart.ecommerce.core.review.dao;

import org.bson.Document;
import rw.smart.ecommerce.core.review.model.ReviewContent;
import rw.smart.ecommerce.utils.BsonValues;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Translates between {@link ReviewContent} and the BSON document shape in
 * nosql/reviews_schema.json.
 *
 * Reads are deliberately lenient — a document store has no schema to enforce, and
 * the seed files use ISO-8601 strings for timestamps while the driver writes real
 * BSON dates — so both forms are accepted and missing fields fall back to empty.
 */
public final class ReviewContentMapper {

    public static final String REVIEW_ID = "review_id";
    public static final String PRODUCT_ID = "product_id";
    public static final String BODY = "body";
    public static final String PHOTOS = "photos";
    public static final String HELPFUL_VOTES = "helpful_votes";
    public static final String SELLER_RESPONSE = "seller_response";
    public static final String EDIT_HISTORY = "edit_history";
    public static final String EDITED_AT = "edited_at";
    public static final String PREVIOUS_BODY = "previous_body";
    public static final String TAGS = "tags";

    private ReviewContentMapper() {
        // utility class, no instances
    }

    public static Document toDocument(ReviewContent content) {
        List<Document> history = new ArrayList<>();
        for (ReviewContent.Edit edit : content.getEditHistory()) {
            history.add(new Document(EDITED_AT, edit.editedAt() == null ? null : Date.from(edit.editedAt()))
                    .append(PREVIOUS_BODY, edit.previousBody()));
        }

        return new Document(REVIEW_ID, content.getReviewId())
                .append(PRODUCT_ID, content.getProductId())
                .append(BODY, content.getBody())
                .append(PHOTOS, new ArrayList<>(content.getPhotos()))
                .append(HELPFUL_VOTES, content.getHelpfulVotes())
                .append(SELLER_RESPONSE, content.getSellerResponse())
                .append(EDIT_HISTORY, history)
                .append(TAGS, new ArrayList<>(content.getTags()));
    }

    public static ReviewContent fromDocument(Document document) {
        ReviewContent content = new ReviewContent();
        content.setReviewId(BsonValues.readInt(document.get(REVIEW_ID)));
        content.setProductId(BsonValues.readInt(document.get(PRODUCT_ID)));
        content.setBody(document.getString(BODY));
        content.setPhotos(document.getList(PHOTOS, String.class, List.of()));
        content.setHelpfulVotes(BsonValues.readInt(document.get(HELPFUL_VOTES)));
        content.setSellerResponse(document.getString(SELLER_RESPONSE));
        content.setTags(document.getList(TAGS, String.class, List.of()));

        List<ReviewContent.Edit> history = new ArrayList<>();
        for (Document edit : document.getList(EDIT_HISTORY, Document.class, List.of())) {
            history.add(new ReviewContent.Edit(
                    BsonValues.readInstant(edit.get(EDITED_AT)), edit.getString(PREVIOUS_BODY)));
        }
        content.setEditHistory(history);
        return content;
    }
}
