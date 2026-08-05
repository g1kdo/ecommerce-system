package rw.smart.ecommerce.core.review.service;

import com.mongodb.MongoTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rw.smart.ecommerce.core.review.dao.ReviewContentDAO;
import rw.smart.ecommerce.core.review.dao.ReviewDAO;
import rw.smart.ecommerce.core.review.model.Review;
import rw.smart.ecommerce.core.review.model.ReviewContent;
import rw.smart.ecommerce.utils.exceptions.DocumentStoreException;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * The hybrid model lives in this service, so these tests pin down which half owns
 * what: the rating goes to SQL first and a document-store failure must never cost
 * a rating that has already been committed.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewDAO reviewDAO;
    @Mock
    private ReviewContentDAO contentDAO;

    private ReviewService reviewService() {
        return new ReviewService(reviewDAO, contentDAO);
    }

    private Review review(int reviewId, int rating, LocalDateTime createdAt) {
        return new Review(reviewId, 501, 1, rating, createdAt);
    }

    @Test
    void recordsARatingRelationally() throws SQLException {
        when(reviewDAO.upsertRating(501, 1, 4)).thenReturn(10245);

        assertEquals(10245, reviewService().rateProduct(501, 1, 4));
        verify(reviewDAO).upsertRating(501, 1, 4);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 6, 99})
    @DisplayName("out-of-range ratings never reach the database")
    void rejectsRatingsOutsideOneToFive(int rating) throws SQLException {
        assertThrows(InvalidInputException.class, () -> reviewService().rateProduct(501, 1, rating));
        verify(reviewDAO, never()).upsertRating(anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("the SQL average is rounded to one decimal for display")
    void roundsAverageToOneDecimal() throws SQLException {
        when(reviewDAO.getAverageRating(501)).thenReturn(new BigDecimal("4.3333333333333333"));

        assertEquals("4.3", reviewService().getAverageRating(501).toPlainString());
    }

    @Test
    void listsReviewsNewestFirstWithUndatedLast() throws SQLException {
        Review older = review(1, 3, LocalDateTime.of(2026, 7, 1, 10, 0));
        Review newer = review(2, 5, LocalDateTime.of(2026, 8, 1, 10, 0));
        Review undated = review(3, 4, null);
        when(reviewDAO.findByProduct(501)).thenReturn(List.of(older, undated, newer));

        List<Review> ordered = reviewService().getReviews(501);

        assertEquals(List.of(2, 1, 3), ordered.stream().map(Review::getReviewId).toList());
    }

    @Test
    @DisplayName("submitting with text writes the rating to SQL and the content to the document store")
    void submitWritesBothHalves() throws SQLException {
        when(reviewDAO.upsertRating(501, 1, 5)).thenReturn(10245);

        int reviewId = reviewService().submitReview(501, 1, 5, "  Great build quality.  ", List.of("durable"));

        assertEquals(10245, reviewId);
        ArgumentCaptor<ReviewContent> saved = ArgumentCaptor.forClass(ReviewContent.class);
        verify(contentDAO).save(saved.capture());
        assertEquals(10245, saved.getValue().getReviewId(), "content must join on review_id");
        assertEquals(501, saved.getValue().getProductId());
        assertEquals("Great build quality.", saved.getValue().getBody());
        assertEquals(List.of("durable"), saved.getValue().getTags());
    }

    @Test
    @DisplayName("a rating with no text writes nothing to the document store")
    void ratingOnlySkipsTheDocumentStore() throws SQLException {
        when(reviewDAO.upsertRating(501, 1, 4)).thenReturn(10245);

        assertEquals(10245, reviewService().submitReview(501, 1, 4, "   ", List.of()));

        verifyNoInteractions(contentDAO);
    }

    @Test
    @DisplayName("a document-store failure reports partial success, keeping the committed rating")
    void documentFailureDoesNotLoseTheRating() throws SQLException {
        when(reviewDAO.upsertRating(501, 1, 5)).thenReturn(10245);
        doThrow(new MongoTimeoutException("no server")).when(contentDAO).save(any(ReviewContent.class));

        DocumentStoreException error = assertThrows(DocumentStoreException.class,
                () -> reviewService().submitReview(501, 1, 5, "Nice", List.of()));

        assertTrue(error.getMessage().startsWith("Rating saved"), "message was: " + error.getMessage());
        verify(reviewDAO).upsertRating(501, 1, 5);
    }

    @Test
    @DisplayName("an invalid rating is rejected before either half is written")
    void invalidRatingWritesNothing() throws SQLException {
        assertThrows(InvalidInputException.class,
                () -> reviewService().submitReview(501, 1, 9, "Nice", List.of()));

        verify(reviewDAO, never()).upsertRating(anyInt(), anyInt(), anyInt());
        verifyNoInteractions(contentDAO);
    }

    @Test
    void readsContentKeyedByReviewId() {
        ReviewContent content = new ReviewContent(10245, 501, "Great");
        when(contentDAO.findByProduct(501)).thenReturn(Map.of(10245, content));

        assertSame(content, reviewService().getReviewContent(501).get(10245));
    }

    @Test
    @DisplayName("an unreachable document store degrades to no content rather than an error")
    void unreachableStoreReturnsEmptyContent() {
        when(contentDAO.findByProduct(501)).thenThrow(new MongoTimeoutException("no server"));

        assertTrue(reviewService().getReviewContent(501).isEmpty());
    }

    @Test
    void marksHelpfulThroughTheDocumentStore() {
        when(contentDAO.markHelpful(10245)).thenReturn(true);

        assertTrue(reviewService().markHelpful(10245));
        verify(contentDAO).markHelpful(10245);
    }
}
