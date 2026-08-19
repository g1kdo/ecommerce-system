package rw.smart.ecommerce.core.report.dto;

import java.time.LocalDate;

/**
 * The two GraphQL report roots.
 *
 * Neither carries data. They exist so that {@code salesReport(from:, to:)} can
 * return something before any report has been run, and each field underneath can
 * then be resolved only if the client actually selected it. A dashboard asking
 * for one panel runs one query; asking for four runs four, in one round trip.
 *
 * {@link SalesReport} carries the requested window down to those field
 * resolvers. {@link CatalogueReport} carries nothing at all — its fields take
 * their own arguments — and is a record purely so Spring for GraphQL has a source
 * object to hang them on.
 */
public final class ReportRoots {

    private ReportRoots() {
        // container for the two root markers
    }

    /**
     * The window is passed through unresolved, nulls included. Defaulting it here
     * as well as in {@code ReportServiceImpl} would put the same rule in two
     * places, and they would eventually disagree.
     */
    public record SalesReport(LocalDate from, LocalDate to) {
    }

    public record CatalogueReport() {
    }
}
