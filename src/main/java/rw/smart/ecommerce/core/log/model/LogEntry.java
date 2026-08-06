package rw.smart.ecommerce.core.log.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One system log document (see docs/nosql/logs_schema.json): write-heavy, never joined,
 * and read by time range or user. {@code eventType} is held as a String rather
 * than an enum so a value this build does not know about still reads back.
 */
public class LogEntry {

    private String logId;
    private Instant timestamp;
    /** Null for events with no signed-in user, e.g. a failed sign-in attempt. */
    private Integer userId;
    private String eventType;
    private Map<String, Object> details = new LinkedHashMap<>();

    public LogEntry() {

    }

    public LogEntry(String logId, Instant timestamp, Integer userId, String eventType, Map<String, Object> details) {
        this.logId = logId;
        this.timestamp = timestamp;
        this.userId = userId;
        this.eventType = eventType;
        setDetails(details);
    }

    public String getLogId() {
        return logId;
    }

    public void setLogId(String logId) {
        this.logId = logId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
    }
}
