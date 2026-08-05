package rw.smart.ecommerce.core.review.dao;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rw.smart.ecommerce.core.review.model.ReviewContent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Document mapping, checked against the committed seed shape in
 * nosql/reviews_schema.json — if that file and this mapper drift apart, this test
 * is what notices.
 */
class ReviewContentMapperTest {

    private static final Path SEED = Path.of("nosql", "reviews_schema.json");

    @Test
    @DisplayName("the committed seed document maps onto the model")
    void readsTheCommittedSeedDocument() throws IOException {
        Document seed = Document.parse(Files.readString(SEED));

        ReviewContent content = ReviewContentMapper.fromDocument(seed);

        assertEquals(10245, content.getReviewId());
        assertEquals(501, content.getProductId());
        assertEquals("Great build quality, arrived a day early.", content.getBody());
        assertEquals(1, content.getPhotos().size());
        assertEquals(12, content.getHelpfulVotes());
        assertNull(content.getSellerResponse());
        assertEquals(List.of("durable", "fast-shipping"), content.getTags());

        assertEquals(1, content.getEditHistory().size());
        ReviewContent.Edit edit = content.getEditHistory().get(0);
        assertEquals("Good product.", edit.previousBody());
        assertEquals(Instant.parse("2026-07-20T09:00:00Z"), edit.editedAt(),
                "the seed stores edited_at as an ISO string, which must still parse");
    }

    @Test
    void writesEveryFieldUnderItsDocumentKey() {
        ReviewContent content = new ReviewContent(10245, 501, "Great");
        content.setPhotos(List.of("https://cdn.example.com/1.jpg"));
        content.setTags(List.of("durable"));
        content.setHelpfulVotes(12);
        content.setSellerResponse("Thanks!");
        content.setEditHistory(List.of(
                new ReviewContent.Edit(Instant.parse("2026-07-20T09:00:00Z"), "Good product.")));

        Document document = ReviewContentMapper.toDocument(content);

        assertEquals(10245, document.getInteger(ReviewContentMapper.REVIEW_ID));
        assertEquals(501, document.getInteger(ReviewContentMapper.PRODUCT_ID));
        assertEquals("Great", document.getString(ReviewContentMapper.BODY));
        assertEquals(12, document.getInteger(ReviewContentMapper.HELPFUL_VOTES));
        assertEquals("Thanks!", document.getString(ReviewContentMapper.SELLER_RESPONSE));
        assertEquals(List.of("durable"), document.getList(ReviewContentMapper.TAGS, String.class));

        List<Document> history = document.getList(ReviewContentMapper.EDIT_HISTORY, Document.class);
        assertEquals(1, history.size());
        assertEquals("Good product.", history.get(0).getString(ReviewContentMapper.PREVIOUS_BODY));
        assertInstanceOf(Date.class, history.get(0).get(ReviewContentMapper.EDITED_AT),
                "timestamps are written as BSON dates so the store can sort on them");
    }

    @Test
    void roundTripsThroughADocument() {
        ReviewContent original = new ReviewContent(1, 2, "Body text");
        original.setPhotos(List.of("a.jpg", "b.jpg"));
        original.setTags(List.of("x"));
        original.setHelpfulVotes(3);
        original.setEditHistory(List.of(new ReviewContent.Edit(Instant.parse("2026-01-01T00:00:00Z"), "old")));

        ReviewContent restored = ReviewContentMapper.fromDocument(ReviewContentMapper.toDocument(original));

        assertEquals(original.getReviewId(), restored.getReviewId());
        assertEquals(original.getBody(), restored.getBody());
        assertEquals(original.getPhotos(), restored.getPhotos());
        assertEquals(original.getTags(), restored.getTags());
        assertEquals(original.getHelpfulVotes(), restored.getHelpfulVotes());
        assertEquals(original.getEditHistory().get(0).previousBody(),
                restored.getEditHistory().get(0).previousBody());
        assertEquals(original.getEditHistory().get(0).editedAt(),
                restored.getEditHistory().get(0).editedAt());
    }

    @Test
    @DisplayName("a sparse document reads without throwing - the store has no schema")
    void toleratesMissingFields() {
        ReviewContent content = ReviewContentMapper.fromDocument(new Document("review_id", 5));

        assertEquals(5, content.getReviewId());
        assertEquals(0, content.getProductId());
        assertNull(content.getBody());
        assertTrue(content.getPhotos().isEmpty());
        assertTrue(content.getTags().isEmpty());
        assertTrue(content.getEditHistory().isEmpty());
        assertEquals(0, content.getHelpfulVotes());
    }

    @Test
    @DisplayName("a long value in an int field is narrowed rather than failing")
    void narrowsNumericTypes() {
        Document document = new Document("review_id", 7L).append("helpful_votes", 4.0);

        ReviewContent content = ReviewContentMapper.fromDocument(document);

        assertEquals(7, content.getReviewId());
        assertEquals(4, content.getHelpfulVotes());
    }
}
