package rw.smart.ecommerce.core.log.dao;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import rw.smart.ecommerce.core.log.model.LogEntry;
import rw.smart.ecommerce.utils.MongoConnection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lt;

/**
 * Document-store access for system logs. Writes are append-only and reads are by
 * recency, user, or time range — the access patterns the log schema was chosen
 * for (docs/nosql/logs_schema.json). Nothing here is ever joined relationally.
 */
public class LogDAO {

    private final Supplier<MongoCollection<Document>> collection;

    public LogDAO() {
        this(() -> MongoConnection.collection(MongoConnection.LOGS_COLLECTION));
    }

    public LogDAO(Supplier<MongoCollection<Document>> collection) {
        this.collection = collection;
    }

    public void insert(LogEntry entry) {
        collection.get().insertOne(LogMapper.toDocument(entry));
    }

    public List<LogEntry> findRecent(int limit) {
        return read(collection.get().find().sort(Sorts.descending(LogMapper.TIMESTAMP)).limit(limit));
    }

    public List<LogEntry> findByUser(int userId, int limit) {
        return read(collection.get().find(eq(LogMapper.USER_ID, userId))
                .sort(Sorts.descending(LogMapper.TIMESTAMP))
                .limit(limit));
    }

    public List<LogEntry> findByEventType(String eventType, int limit) {
        return read(collection.get().find(eq(LogMapper.EVENT_TYPE, eventType))
                .sort(Sorts.descending(LogMapper.TIMESTAMP))
                .limit(limit));
    }

    /** Time-range scan: {@code [from, to)}, the natural query for a log store. */
    public List<LogEntry> findBetween(Instant from, Instant to, int limit) {
        return read(collection.get().find(and(
                        gte(LogMapper.TIMESTAMP, Date.from(from)),
                        lt(LogMapper.TIMESTAMP, Date.from(to))))
                .sort(Sorts.descending(LogMapper.TIMESTAMP))
                .limit(limit));
    }

    private List<LogEntry> read(FindIterable<Document> documents) {
        List<LogEntry> entries = new ArrayList<>();
        for (Document document : documents) {
            entries.add(LogMapper.fromDocument(document));
        }
        return entries;
    }
}
