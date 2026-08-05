package rw.smart.ecommerce.core.log.dao;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import rw.smart.ecommerce.core.log.enums.EventType;
import rw.smart.ecommerce.core.log.model.LogEntry;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Log DAO behaviour against a mocked collection: append on write, sorted+capped on read. */
class LogDAOTest {

    private MongoCollection<Document> collection;
    private FindIterable<Document> findIterable;
    private LogDAO logDAO;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        collection = mock(MongoCollection.class);
        findIterable = mock(FindIterable.class);
        when(findIterable.sort(any(Bson.class))).thenReturn(findIterable);
        when(findIterable.limit(anyInt())).thenReturn(findIterable);
        logDAO = new LogDAO(() -> collection);
    }

    @SuppressWarnings("unchecked")
    private void stubOneResult() {
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(new Document(LogMapper.LOG_ID, "log_9f3a2b1c")
                .append(LogMapper.EVENT_TYPE, "PRODUCT_SEARCH")
                .append(LogMapper.USER_ID, 88)
                .append(LogMapper.DETAILS, new Document("query", "wireless mouse")));
        when(findIterable.iterator()).thenReturn(cursor);
    }

    @Test
    void insertWritesTheDocument() {
        LogEntry entry = new LogEntry("log_abc12345", Instant.parse("2026-08-02T10:15:00Z"), 88,
                EventType.PRODUCT_SEARCH.name(), Map.of("query", "mouse"));

        logDAO.insert(entry);

        ArgumentCaptor<Document> written = ArgumentCaptor.forClass(Document.class);
        verify(collection).insertOne(written.capture());
        assertEquals("log_abc12345", written.getValue().getString(LogMapper.LOG_ID));
        assertEquals("PRODUCT_SEARCH", written.getValue().getString(LogMapper.EVENT_TYPE));
    }

    @Test
    @DisplayName("recent events are capped by the requested limit")
    void findRecentSortsAndLimits() {
        when(collection.find()).thenReturn(findIterable);
        stubOneResult();

        List<LogEntry> entries = logDAO.findRecent(50);

        assertEquals(1, entries.size());
        assertEquals("log_9f3a2b1c", entries.get(0).getLogId());
        verify(findIterable).sort(any(Bson.class));
        verify(findIterable).limit(50);
    }

    @Test
    void findByUserQueriesOnUserId() {
        when(collection.find(any(Bson.class))).thenReturn(findIterable);
        stubOneResult();

        assertEquals(1, logDAO.findByUser(88, 25).size());
        verify(findIterable).limit(25);
    }

    @Test
    void findByEventTypeQueriesOnEventType() {
        when(collection.find(any(Bson.class))).thenReturn(findIterable);
        stubOneResult();

        List<LogEntry> entries = logDAO.findByEventType("PRODUCT_SEARCH", 10);

        assertEquals(1, entries.size());
        assertEquals("PRODUCT_SEARCH", entries.get(0).getEventType());
    }

    @Test
    @DisplayName("a time-range read builds a bounded query")
    void findBetweenQueriesATimeRange() {
        when(collection.find(any(Bson.class))).thenReturn(findIterable);
        stubOneResult();

        List<LogEntry> entries = logDAO.findBetween(
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-03T00:00:00Z"), 100);

        assertEquals(1, entries.size());
        verify(collection).find(any(Bson.class));
        verify(findIterable).limit(100);
    }

    @Test
    void emptyCollectionReadsAsAnEmptyList() {
        when(collection.find()).thenReturn(findIterable);
        @SuppressWarnings("unchecked")
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenReturn(false);
        when(findIterable.iterator()).thenReturn(cursor);

        assertTrue(logDAO.findRecent(50).isEmpty());
    }
}
