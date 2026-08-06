package rw.smart.ecommerce.core.log.dao;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rw.smart.ecommerce.core.log.enums.EventType;
import rw.smart.ecommerce.core.log.model.LogEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Log document mapping, checked against the committed nosql/logs_schema.json shape. */
class LogMapperTest {

    private static final Path SEED = Path.of("docs", "nosql", "logs_schema.json");

    @Test
    @DisplayName("the committed seed document maps onto the model")
    void readsTheCommittedSeedDocument() throws IOException {
        Document seed = Document.parse(Files.readString(SEED));

        LogEntry entry = LogMapper.fromDocument(seed);

        assertEquals("log_9f3a2b1c", entry.getLogId());
        assertEquals(88, entry.getUserId());
        assertEquals("PRODUCT_SEARCH", entry.getEventType());
        assertEquals(Instant.parse("2026-08-02T10:15:00Z"), entry.getTimestamp());
        assertEquals("wireless mouse", entry.getDetails().get("query"));
        assertEquals(34, entry.getDetails().get("results_count"));
        assertEquals(true, entry.getDetails().get("cache_hit"));
    }

    @Test
    @DisplayName("the seed's event type resolves to a display label")
    void seedEventTypeHasALabel() throws IOException {
        Document seed = Document.parse(Files.readString(SEED));

        assertEquals("Product search", EventType.labelOf(LogMapper.fromDocument(seed).getEventType()));
    }

    @Test
    void writesEveryFieldUnderItsDocumentKey() {
        LogEntry entry = new LogEntry("log_abc12345", Instant.parse("2026-08-02T10:15:00Z"), 88,
                EventType.ORDER_PLACED.name(), Map.of("order_id", 42));

        Document document = LogMapper.toDocument(entry);

        assertEquals("log_abc12345", document.getString(LogMapper.LOG_ID));
        assertEquals(88, document.getInteger(LogMapper.USER_ID));
        assertEquals("ORDER_PLACED", document.getString(LogMapper.EVENT_TYPE));
        assertInstanceOf(Date.class, document.get(LogMapper.TIMESTAMP));
        assertEquals(42, document.get(LogMapper.DETAILS, Document.class).getInteger("order_id"));
    }

    @Test
    void roundTripsThroughADocument() {
        LogEntry original = new LogEntry("log_abc12345", Instant.parse("2026-08-02T10:15:00Z"), 7,
                EventType.STOCK_UPDATED.name(), Map.of("product_id", 3, "quantity", 45));

        LogEntry restored = LogMapper.fromDocument(LogMapper.toDocument(original));

        assertEquals(original.getLogId(), restored.getLogId());
        assertEquals(original.getTimestamp(), restored.getTimestamp());
        assertEquals(original.getUserId(), restored.getUserId());
        assertEquals(original.getEventType(), restored.getEventType());
        assertEquals(original.getDetails(), restored.getDetails());
    }

    @Test
    @DisplayName("an entry with no user and no details reads back cleanly")
    void toleratesMissingFields() {
        LogEntry entry = LogMapper.fromDocument(new Document(LogMapper.EVENT_TYPE, "LOGIN_FAILED"));

        assertNull(entry.getUserId());
        assertNull(entry.getTimestamp());
        assertEquals("LOGIN_FAILED", entry.getEventType());
        assertTrue(entry.getDetails().isEmpty());
    }
}
