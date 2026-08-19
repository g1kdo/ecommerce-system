package rw.smart.ecommerce.utils;

import rw.smart.ecommerce.utils.exceptions.InvalidInputException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Parses the ISO-8601 date strings the GraphQL schema uses for report windows.
 *
 * The schema deliberately has no custom {@code Date} scalar — timestamps have
 * been plain strings since Phase 2, and adding a scalar now would change the
 * wire format of every existing field for the sake of four arguments.
 *
 * The cost of that decision is that a malformed date arrives as a valid
 * {@code String} and is only rejected here. So it is rejected properly: a
 * {@link InvalidInputException} naming the offending argument, which the GraphQL
 * exception resolver turns into a {@code BAD_REQUEST} error, rather than a
 * {@link DateTimeParseException} surfacing as an internal error with a stack
 * trace attached.
 */
public final class GraphQlDates {

    private GraphQlDates() {
        // utility class, no instances
    }

    /**
     * @param value        an ISO-8601 date, or null/blank for "not supplied"
     * @param argumentName the schema argument being parsed, used in the message
     * @return the parsed date, or null — which every report treats as "use the
     *         default window", resolved once in {@code ReportServiceImpl}
     */
    public static LocalDate parse(String value, String argumentName) {
        if (value == null || value.isBlank()) return null;

        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException(
                    "Argument '" + argumentName + "' must be an ISO-8601 date (YYYY-MM-DD), got: " + value);
        }
    }
}
