package rw.smart.ecommerce.core.log.service;

import com.mongodb.MongoTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rw.smart.ecommerce.core.log.dao.LogDAO;
import rw.smart.ecommerce.core.log.enums.EventType;
import rw.smart.ecommerce.core.log.model.LogEntry;
import rw.smart.ecommerce.core.user.model.User;
import rw.smart.ecommerce.utils.session.Session;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Logging is best-effort by design: it must record the signed-in user and the event
 * details, and it must never propagate a failure into the action being logged.
 * The executor is replaced with a direct one so writes are synchronous here.
 */
@ExtendWith(MockitoExtension.class)
class LogServiceTest {

    @Mock
    private LogDAO logDAO;

    private LogService logService() {
        return new LogService(logDAO, Runnable::run);
    }

    private void signIn(int userId) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername("jdoe");
        user.setFullName("John Doe");
        Session.login(user);
    }

    @AfterEach
    void clearSession() {
        Session.logout();
    }

    @Test
    @DisplayName("an event records the signed-in user, the type name, and the details")
    void writesEventForSignedInUser() {
        signIn(88);

        logService().log(EventType.PRODUCT_SEARCH,
                Map.of("query", "wireless mouse", "results_count", 34, "cache_hit", true));

        ArgumentCaptor<LogEntry> written = ArgumentCaptor.forClass(LogEntry.class);
        verify(logDAO).insert(written.capture());
        LogEntry entry = written.getValue();

        assertEquals(88, entry.getUserId());
        assertEquals("PRODUCT_SEARCH", entry.getEventType(), "the stored value is the enum name, not the label");
        assertEquals("wireless mouse", entry.getDetails().get("query"));
        assertEquals(34, entry.getDetails().get("results_count"));
        assertEquals(true, entry.getDetails().get("cache_hit"));
        assertNotNull(entry.getTimestamp());
        assertTrue(entry.getLogId().startsWith("log_"), "log id was: " + entry.getLogId());
    }

    @Test
    @DisplayName("events without a session are recorded with no user id")
    void writesEventWithoutSession() {
        logService().log(EventType.LOGIN_FAILED, null, Map.of("reason", "invalid_credentials"));

        ArgumentCaptor<LogEntry> written = ArgumentCaptor.forClass(LogEntry.class);
        verify(logDAO).insert(written.capture());

        assertNull(written.getValue().getUserId());
        assertEquals("LOGIN_FAILED", written.getValue().getEventType());
    }

    @Test
    void anonymousEventWhenNobodyIsSignedIn() {
        logService().log(EventType.PRODUCT_SEARCH, Map.of("query", "pan"));

        ArgumentCaptor<LogEntry> written = ArgumentCaptor.forClass(LogEntry.class);
        verify(logDAO).insert(written.capture());

        assertNull(written.getValue().getUserId());
    }

    @Test
    @DisplayName("log ids are unique per event")
    void generatesUniqueLogIds() {
        signIn(1);
        logService().log(EventType.LOGIN, Map.of());
        logService().log(EventType.LOGOUT, Map.of());

        ArgumentCaptor<LogEntry> written = ArgumentCaptor.forClass(LogEntry.class);
        verify(logDAO, times(2)).insert(written.capture());

        assertNotEquals(written.getAllValues().get(0).getLogId(), written.getAllValues().get(1).getLogId());
    }

    @Test
    @DisplayName("an unreachable log store must not break the action being logged")
    void swallowsWriteFailures() {
        signIn(1);
        doThrow(new MongoTimeoutException("no server")).when(logDAO).insert(any(LogEntry.class));

        assertDoesNotThrow(() -> logService().log(EventType.ORDER_PLACED, Map.of("order_id", 42)));
    }

    @Test
    void readsDelegateToTheDao() {
        LogEntry entry = new LogEntry();
        when(logDAO.findRecent(50)).thenReturn(List.of(entry));
        when(logDAO.findByUser(88, 50)).thenReturn(List.of(entry));
        when(logDAO.findByEventType("ORDER_PLACED", 50)).thenReturn(List.of(entry));

        assertEquals(1, logService().getRecent(50).size());
        assertEquals(1, logService().getForUser(88, 50).size());
        assertEquals(1, logService().getByEventType(EventType.ORDER_PLACED, 50).size());
    }

    @Test
    @DisplayName("unknown stored event types still display, falling back to the raw value")
    void unknownEventTypesFallBackToRawValue() {
        assertEquals("Product search", EventType.labelOf("PRODUCT_SEARCH"));
        assertEquals("SOMETHING_NEW", EventType.labelOf("SOMETHING_NEW"));
        assertEquals("", EventType.labelOf(null));
    }
}
