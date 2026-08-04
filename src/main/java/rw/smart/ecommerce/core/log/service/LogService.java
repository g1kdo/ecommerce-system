package rw.smart.ecommerce.core.log.service;

import rw.smart.ecommerce.core.log.dao.LogDAO;
import rw.smart.ecommerce.core.log.enums.EventType;
import rw.smart.ecommerce.core.log.model.LogEntry;
import rw.smart.ecommerce.utils.MongoConnection;
import rw.smart.ecommerce.utils.session.Session;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Writes and reads the system log collection.
 *
 * Two properties matter here and both are deliberate:
 * <ul>
 *   <li><b>Logging never breaks a user action.</b> Write failures are swallowed
 *       (reported once to stderr), because an unreachable log store must not fail
 *       a checkout.</li>
 *   <li><b>Logging never blocks the UI.</b> Writes are handed to a daemon thread,
 *       so a slow or missing server costs the FX thread nothing.</li>
 * </ul>
 */
public class LogService {

    private static final Executor ASYNC_WRITER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "log-writer");
        thread.setDaemon(true);
        return thread;
    });

    /** Stops an unreachable store from printing a warning per event. */
    private static final AtomicBoolean WRITE_FAILURE_REPORTED = new AtomicBoolean(false);

    private final LogDAO logDAO;
    private final Executor writer;

    public LogService() {
        this(new LogDAO(), ASYNC_WRITER);
    }

    /** Tests pass a direct executor ({@code Runnable::run}) to make writes synchronous. */
    public LogService(LogDAO logDAO, Executor writer) {
        this.logDAO = logDAO;
        this.writer = writer;
    }

    public void log(EventType eventType, Map<String, Object> details) {
        log(eventType, Session.isAuthenticated() ? Session.currentUserId() : null, details);
    }

    public void log(EventType eventType, Integer userId, Map<String, Object> details) {
        LogEntry entry = new LogEntry(newLogId(), Instant.now(), userId, eventType.name(), details);
        writer.execute(() -> write(entry));
    }

    private void write(LogEntry entry) {
        try {
            logDAO.insert(entry);
        } catch (RuntimeException e) {
            if (WRITE_FAILURE_REPORTED.compareAndSet(false, true)) {
                System.err.println("System logging disabled - document store unreachable: " + e.getMessage());
            }
        }
    }

    public List<LogEntry> getRecent(int limit) {
        return logDAO.findRecent(limit);
    }

    public List<LogEntry> getForUser(int userId, int limit) {
        return logDAO.findByUser(userId, limit);
    }

    public List<LogEntry> getByEventType(EventType eventType, int limit) {
        return logDAO.findByEventType(eventType.name(), limit);
    }

    /** Whether the log store can be read right now, for screens that display it. */
    public boolean isAvailable() {
        return MongoConnection.isAvailable();
    }

    private String newLogId() {
        return "log_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
