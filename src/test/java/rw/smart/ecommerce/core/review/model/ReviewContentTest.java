package rw.smart.ecommerce.core.review.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The merge rules that make an author's edit safe: server-owned fields survive it,
 * and the superseded text is kept.
 */
class ReviewContentTest {

    private static final Instant EDITED_AT = Instant.parse("2026-08-04T12:00:00Z");

    private ReviewContent stored(String body, int votes, String sellerResponse) {
        ReviewContent content = new ReviewContent(10245, 501, body);
        content.setHelpfulVotes(votes);
        content.setSellerResponse(sellerResponse);
        return content;
    }

    @Test
    @DisplayName("a first save keeps its own values and has no history")
    void firstSaveKeepsOwnValues() {
        ReviewContent incoming = stored("First body", 0, null);
        incoming.setTags(List.of("durable"));

        ReviewContent revision = incoming.revisionOf(null, EDITED_AT);

        assertEquals("First body", revision.getBody());
        assertEquals(List.of("durable"), revision.getTags());
        assertTrue(revision.getEditHistory().isEmpty());
    }

    @Test
    @DisplayName("an edit appends the previous body to the history")
    void editRecordsPreviousBody() {
        ReviewContent previous = stored("Good product.", 12, "Thanks!");
        ReviewContent incoming = stored("Actually excellent.", 0, null);

        ReviewContent revision = incoming.revisionOf(previous, EDITED_AT);

        assertEquals(1, revision.getEditHistory().size());
        assertEquals("Good product.", revision.getEditHistory().get(0).previousBody());
        assertEquals(EDITED_AT, revision.getEditHistory().get(0).editedAt());
    }

    @Test
    @DisplayName("an edit cannot reset votes or wipe the seller response")
    void editPreservesServerOwnedFields() {
        ReviewContent previous = stored("Good product.", 12, "Thanks!");
        ReviewContent incoming = stored("Actually excellent.", 0, null);

        ReviewContent revision = incoming.revisionOf(previous, EDITED_AT);

        assertEquals(12, revision.getHelpfulVotes());
        assertEquals("Thanks!", revision.getSellerResponse());
    }

    @Test
    void unchangedBodyAddsNoHistoryEntry() {
        ReviewContent previous = stored("Same text", 3, null);
        ReviewContent incoming = stored("Same text", 0, null);

        assertTrue(incoming.revisionOf(previous, EDITED_AT).getEditHistory().isEmpty());
    }

    @Test
    void repeatedEditsAccumulateHistory() {
        ReviewContent first = stored("v1", 1, null);
        ReviewContent secondSave = stored("v2", 0, null).revisionOf(first, EDITED_AT);
        ReviewContent thirdSave = stored("v3", 0, null).revisionOf(secondSave, EDITED_AT);

        assertEquals(List.of("v1", "v2"),
                thirdSave.getEditHistory().stream().map(ReviewContent.Edit::previousBody).toList());
    }

    @Test
    @DisplayName("edit history is not modifiable through the getter")
    void editHistoryIsNotExposedForMutation() {
        ReviewContent content = stored("body", 0, null);

        assertThrows(UnsupportedOperationException.class,
                () -> content.getEditHistory().add(new ReviewContent.Edit(EDITED_AT, "sneaky")));
    }

    @Test
    void nullCollectionsBecomeEmptyNotNull() {
        ReviewContent content = new ReviewContent();
        content.setPhotos(null);
        content.setTags(null);
        content.setEditHistory(null);

        assertTrue(content.getPhotos().isEmpty());
        assertTrue(content.getTags().isEmpty());
        assertTrue(content.getEditHistory().isEmpty());
    }
}
