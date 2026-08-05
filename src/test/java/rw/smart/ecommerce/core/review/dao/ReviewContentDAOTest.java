package rw.smart.ecommerce.core.review.dao;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rw.smart.ecommerce.core.review.model.ReviewContent;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Exercises the document DAO against a mocked collection, so the upsert semantics
 * (edit history, preserved votes, atomic $inc) are verified without a running
 * MongoDB.
 */
class ReviewContentDAOTest {

    private MongoCollection<Document> collection;
    private FindIterable<Document> findIterable;
    private ReviewContentDAO reviewContentDAO;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        collection = mock(MongoCollection.class);
        findIterable = mock(FindIterable.class);
        when(collection.find(any(Bson.class))).thenReturn(findIterable);
        reviewContentDAO = new ReviewContentDAO(() -> collection);
    }

    private Document storedDocument(String body, int votes) {
        return new Document(ReviewContentMapper.REVIEW_ID, 10245)
                .append(ReviewContentMapper.PRODUCT_ID, 501)
                .append(ReviewContentMapper.BODY, body)
                .append(ReviewContentMapper.HELPFUL_VOTES, votes)
                .append(ReviewContentMapper.SELLER_RESPONSE, "We are glad you like it");
    }

    private Document captureReplacement() {
        var replacement = org.mockito.ArgumentCaptor.forClass(Document.class);
        verify(collection).replaceOne(any(Bson.class), replacement.capture(), any(ReplaceOptions.class));
        return replacement.getValue();
    }

    @Test
    void findsContentByReviewId() {
        when(findIterable.first()).thenReturn(storedDocument("Great", 12));

        ReviewContent content = reviewContentDAO.findByReviewId(10245);

        assertNotNull(content);
        assertEquals("Great", content.getBody());
        assertEquals(12, content.getHelpfulVotes());
    }

    @Test
    void returnsNullWhenAReviewHasNoContent() {
        when(findIterable.first()).thenReturn(null);

        assertNull(reviewContentDAO.findByReviewId(999));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findsAllContentForAProductKeyedByReviewId() {
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(storedDocument("Great", 12));
        when(findIterable.iterator()).thenReturn(cursor);

        Map<Integer, ReviewContent> byReviewId = reviewContentDAO.findByProduct(501);

        assertEquals(1, byReviewId.size());
        assertEquals("Great", byReviewId.get(10245).getBody());
    }

    @Test
    @DisplayName("a first save upserts with no edit history")
    void firstSaveHasNoHistory() {
        when(findIterable.first()).thenReturn(null);

        ReviewContent content = new ReviewContent(10245, 501, "First impression");
        content.setTags(List.of("durable"));
        reviewContentDAO.save(content);

        Document replacement = captureReplacement();
        assertEquals("First impression", replacement.getString(ReviewContentMapper.BODY));
        assertTrue(replacement.getList(ReviewContentMapper.EDIT_HISTORY, Document.class).isEmpty());
        assertEquals(List.of("durable"), replacement.getList(ReviewContentMapper.TAGS, String.class));
    }

    @Test
    @DisplayName("editing the body records the previous version and keeps votes and seller response")
    void editAppendsHistoryAndPreservesServerOwnedFields() {
        when(findIterable.first()).thenReturn(storedDocument("Good product.", 12));

        reviewContentDAO.save(new ReviewContent(10245, 501, "Actually excellent."));

        Document replacement = captureReplacement();
        assertEquals("Actually excellent.", replacement.getString(ReviewContentMapper.BODY));
        assertEquals(12, replacement.getInteger(ReviewContentMapper.HELPFUL_VOTES),
                "an author edit must not reset the vote count");
        assertEquals("We are glad you like it", replacement.getString(ReviewContentMapper.SELLER_RESPONSE));

        List<Document> history = replacement.getList(ReviewContentMapper.EDIT_HISTORY, Document.class);
        assertEquals(1, history.size());
        assertEquals("Good product.", history.get(0).getString(ReviewContentMapper.PREVIOUS_BODY));
        assertNotNull(history.get(0).get(ReviewContentMapper.EDITED_AT));
    }

    @Test
    @DisplayName("re-saving identical text does not invent an edit")
    void unchangedBodyAddsNoHistory() {
        when(findIterable.first()).thenReturn(storedDocument("Good product.", 12));

        reviewContentDAO.save(new ReviewContent(10245, 501, "Good product."));

        assertTrue(captureReplacement().getList(ReviewContentMapper.EDIT_HISTORY, Document.class).isEmpty());
    }

    @Test
    void saveUpsertsSoTheDocumentIsCreatedIfAbsent() {
        when(findIterable.first()).thenReturn(null);

        reviewContentDAO.save(new ReviewContent(10245, 501, "Body"));

        var options = org.mockito.ArgumentCaptor.forClass(ReplaceOptions.class);
        verify(collection).replaceOne(any(Bson.class), any(Document.class), options.capture());
        assertTrue(options.getValue().isUpsert());
    }

    @Test
    void marksHelpfulWhenTheDocumentExists() {
        UpdateResult result = mock(UpdateResult.class);
        when(result.getModifiedCount()).thenReturn(1L);
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(result);

        assertTrue(reviewContentDAO.markHelpful(10245));
    }

    @Test
    @DisplayName("voting on a rating with no content document reports false")
    void markHelpfulIsFalseWhenNothingMatched() {
        UpdateResult result = mock(UpdateResult.class);
        when(result.getModifiedCount()).thenReturn(0L);
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(result);

        assertFalse(reviewContentDAO.markHelpful(10245));
    }
}
