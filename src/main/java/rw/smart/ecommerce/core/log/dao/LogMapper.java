package rw.smart.ecommerce.core.log.dao;

import org.bson.Document;
import rw.smart.ecommerce.core.log.model.LogEntry;
import rw.smart.ecommerce.utils.BsonValues;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates between {@link LogEntry} and the document shape in
 * nosql/logs_schema.json. Like review content, reads accept either BSON dates or
 * ISO-8601 strings so hand-written seed documents load unchanged.
 */
public final class LogMapper {

    public static final String LOG_ID = "log_id";
    public static final String TIMESTAMP = "timestamp";
    public static final String USER_ID = "user_id";
    public static final String EVENT_TYPE = "event_type";
    public static final String DETAILS = "details";

    private LogMapper() {
        // utility class, no instances
    }

    public static Document toDocument(LogEntry entry) {
        return new Document(LOG_ID, entry.getLogId())
                .append(TIMESTAMP, entry.getTimestamp() == null ? null : Date.from(entry.getTimestamp()))
                .append(USER_ID, entry.getUserId())
                .append(EVENT_TYPE, entry.getEventType())
                .append(DETAILS, new Document(entry.getDetails()));
    }

    public static LogEntry fromDocument(Document document) {
        LogEntry entry = new LogEntry();
        entry.setLogId(document.getString(LOG_ID));
        entry.setTimestamp(BsonValues.readInstant(document.get(TIMESTAMP)));
        entry.setUserId(BsonValues.readNullableInt(document.get(USER_ID)));
        entry.setEventType(document.getString(EVENT_TYPE));

        Map<String, Object> details = new LinkedHashMap<>();
        if (document.get(DETAILS) instanceof Document nested) {
            details.putAll(nested);
        }
        entry.setDetails(details);
        return entry;
    }
}
