package rw.smart.ecommerce.utils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * Lenient readers for values coming out of the document store. A schema-loose
 * collection can legitimately hold the same field in more than one representation
 * — the driver writes BSON dates while the hand-written seed files in nosql/ use
 * ISO-8601 strings — so readers accept either instead of throwing.
 */
public final class BsonValues {

    private BsonValues() {
        // utility class, no instances
    }

    /** A BSON date, an Instant, or an ISO-8601 string; null when absent or unparseable. */
    public static Instant readInstant(Object value) {
        if (value instanceof Date date) return date.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Instant.parse(text.trim());
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }

    /** Any numeric value narrowed to int; 0 when absent or non-numeric. */
    public static int readInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /** Any numeric value as a boxed Integer; null when absent or non-numeric. */
    public static Integer readNullableInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    /** Any numeric value as a boxed Long; null when absent or non-numeric. */
    public static Long readNullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
