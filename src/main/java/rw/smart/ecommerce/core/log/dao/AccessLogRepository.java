package rw.smart.ecommerce.core.log.dao;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Repository;
import rw.smart.ecommerce.config.MongoSettings;
import rw.smart.ecommerce.core.log.model.AccessLog;
import rw.smart.ecommerce.utils.BsonValues;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Append-only store for the monitoring aspect's output. Writes are fire-and-
 * forget from the caller's point of view — see {@code AccessLogService}, which
 * swallows failures so a down log store never fails a business request.
 */
@Slf4j
@Repository
public class AccessLogRepository {

    static final String ID = "_id";
    static final String TIMESTAMP = "timestamp";
    static final String CLASS_NAME = "className";
    static final String METHOD_NAME = "methodName";
    static final String REQUEST_PATH = "requestPath";
    static final String HTTP_METHOD = "httpMethod";
    static final String EXECUTION_TIME_MS = "executionTimeMs";
    static final String OUTCOME = "outcome";
    static final String ERROR_MESSAGE = "errorMessage";

    private final MongoDatabase database;
    private final String collectionName;

    public AccessLogRepository(MongoDatabase database, MongoSettings settings) {
        this.database = database;
        this.collectionName = settings.getAccessLogCollection();
    }

    private MongoCollection<Document> collection() {
        return database.getCollection(collectionName);
    }

    /** Logs are read by recency and by slowest-first; both get an index. */
    @PostConstruct
    void ensureIndexes() {
        try {
            collection().createIndex(Indexes.descending(TIMESTAMP));
            collection().createIndex(Indexes.descending(EXECUTION_TIME_MS));
        } catch (RuntimeException e) {
            log.warn("Could not create access-log indexes: {}", e.getMessage());
        }
    }

    public void insert(AccessLog entry) {
        Document document = new Document()
                .append(TIMESTAMP, entry.getTimestamp() == null ? null : Date.from(entry.getTimestamp()))
                .append(CLASS_NAME, entry.getClassName())
                .append(METHOD_NAME, entry.getMethodName())
                .append(REQUEST_PATH, entry.getRequestPath())
                .append(HTTP_METHOD, entry.getHttpMethod())
                .append(EXECUTION_TIME_MS, entry.getExecutionTimeMs())
                .append(OUTCOME, entry.getOutcome())
                .append(ERROR_MESSAGE, entry.getErrorMessage());

        collection().insertOne(document);
    }

    public List<AccessLog> findRecent(int limit) {
        return read(collection().find().sort(Sorts.descending(TIMESTAMP)).limit(limit));
    }

    /** Time-range scan — the natural query shape for a log collection. */
    public List<AccessLog> findBetween(Instant from, Instant to, int limit) {
        return read(collection().find(Filters.and(
                        Filters.gte(TIMESTAMP, Date.from(from)),
                        Filters.lt(TIMESTAMP, Date.from(to))))
                .sort(Sorts.descending(TIMESTAMP))
                .limit(limit));
    }

    /** Performance triage: everything that took longer than a threshold. */
    public List<AccessLog> findSlowerThan(long thresholdMs, int limit) {
        return read(collection().find(Filters.gt(EXECUTION_TIME_MS, thresholdMs))
                .sort(Sorts.descending(EXECUTION_TIME_MS))
                .limit(limit));
    }

    private List<AccessLog> read(FindIterable<Document> documents) {
        List<AccessLog> entries = new ArrayList<>();
        for (Document document : documents) {
            AccessLog entry = new AccessLog();
            Object id = document.get(ID);
            entry.setId(id == null ? null : id.toString());
            entry.setTimestamp(BsonValues.readInstant(document.get(TIMESTAMP)));
            entry.setClassName(document.getString(CLASS_NAME));
            entry.setMethodName(document.getString(METHOD_NAME));
            entry.setRequestPath(document.getString(REQUEST_PATH));
            entry.setHttpMethod(document.getString(HTTP_METHOD));
            entry.setExecutionTimeMs(BsonValues.readNullableLong(document.get(EXECUTION_TIME_MS)));
            entry.setOutcome(document.getString(OUTCOME));
            entry.setErrorMessage(document.getString(ERROR_MESSAGE));
            entries.add(entry);
        }
        return entries;
    }
}
