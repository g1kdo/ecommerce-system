package rw.smart.ecommerce.core.review.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The variable-shape half of a review, stored as a document (see
 * docs/nosql/reviews_schema.json). The rating and its foreign keys stay in the
 * relational {@link Review} row; everything here — free text, photos, votes,
 * seller responses, edit history — would otherwise need a wide table of nullable
 * columns or an EAV table.
 *
 * {@code review_id} is the join key between the two halves.
 */
public class ReviewContent {

    /** One superseded version of the body, kept so edits are auditable. */
    public record Edit(Instant editedAt, String previousBody) {
    }

    private int reviewId;
    private int productId;
    private String body;
    private List<String> photos = new ArrayList<>();
    private int helpfulVotes;
    private String sellerResponse;
    private List<Edit> editHistory = new ArrayList<>();
    private List<String> tags = new ArrayList<>();

    public ReviewContent() {

    }

    public ReviewContent(int reviewId, int productId, String body) {
        this.reviewId = reviewId;
        this.productId = productId;
        this.body = body;
    }

    public int getReviewId() {
        return reviewId;
    }

    public void setReviewId(int reviewId) {
        this.reviewId = reviewId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public List<String> getPhotos() {
        return photos;
    }

    public void setPhotos(List<String> photos) {
        this.photos = photos == null ? new ArrayList<>() : new ArrayList<>(photos);
    }

    public int getHelpfulVotes() {
        return helpfulVotes;
    }

    public void setHelpfulVotes(int helpfulVotes) {
        this.helpfulVotes = helpfulVotes;
    }

    public String getSellerResponse() {
        return sellerResponse;
    }

    public void setSellerResponse(String sellerResponse) {
        this.sellerResponse = sellerResponse;
    }

    public List<Edit> getEditHistory() {
        return Collections.unmodifiableList(editHistory);
    }

    public void setEditHistory(List<Edit> editHistory) {
        this.editHistory = editHistory == null ? new ArrayList<>() : new ArrayList<>(editHistory);
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    /**
     * Builds the document to store when this content replaces {@code previous}.
     *
     * Server-owned fields (helpful votes, the seller's response) are carried over
     * rather than overwritten by an author's edit, and a body change is recorded
     * in the edit history instead of being lost.
     *
     * @param previous the stored version, or null when this is the first save
     * @param editedAt timestamp for the history entry, if one is added
     */
    public ReviewContent revisionOf(ReviewContent previous, Instant editedAt) {
        ReviewContent revision = new ReviewContent(reviewId, productId, body);
        revision.setPhotos(photos);
        revision.setTags(tags);

        if (previous == null) {
            revision.setHelpfulVotes(helpfulVotes);
            revision.setSellerResponse(sellerResponse);
            revision.setEditHistory(editHistory);
            return revision;
        }

        revision.setHelpfulVotes(previous.getHelpfulVotes());
        revision.setSellerResponse(previous.getSellerResponse());

        List<Edit> history = new ArrayList<>(previous.getEditHistory());
        if (!Objects.equals(previous.getBody(), body)) {
            history.add(new Edit(editedAt, previous.getBody()));
        }
        revision.setEditHistory(history);
        return revision;
    }
}
