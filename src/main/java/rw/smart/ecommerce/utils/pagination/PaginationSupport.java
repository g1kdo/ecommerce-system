package rw.smart.ecommerce.utils.pagination;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;

import java.util.Set;

/**
 * Turns raw query parameters into a {@link Pageable}.
 *
 * {@code sortBy} is checked against a whitelist rather than handed straight to
 * Spring Data: an unknown property would otherwise surface as a
 * {@code PropertyReferenceException} — a 500 that leaks entity internals — where
 * the honest answer is a 400.
 */
@Component
public class PaginationSupport {

    private static final Set<String> SORTABLE_PRODUCT_FIELDS = Set.of("id", "name", "price", "createdAt", "sku");
    private static final Set<String> SORTABLE_USER_FIELDS = Set.of("id", "username", "email", "fullName", "createdAt");
    private static final Set<String> SORTABLE_ORDER_FIELDS = Set.of("id", "orderDate", "totalAmount", "status");
    private static final Set<String> SORTABLE_CATEGORY_FIELDS = Set.of("id", "name");

    private final int defaultPageSize;
    private final int maxPageSize;

    public PaginationSupport(
            @Value("${app.pagination.default-page-size:20}") int defaultPageSize,
            @Value("${app.pagination.max-page-size:100}") int maxPageSize) {
        this.defaultPageSize = defaultPageSize;
        this.maxPageSize = maxPageSize;
    }

    public Pageable forProducts(Integer page, Integer size, String sortBy, String direction) {
        return build(page, size, sortBy, direction, SORTABLE_PRODUCT_FIELDS, "id");
    }

    public Pageable forUsers(Integer page, Integer size, String sortBy, String direction) {
        return build(page, size, sortBy, direction, SORTABLE_USER_FIELDS, "id");
    }

    public Pageable forOrders(Integer page, Integer size, String sortBy, String direction) {
        return build(page, size, sortBy, direction, SORTABLE_ORDER_FIELDS, "orderDate");
    }

    public Pageable forCategories(Integer page, Integer size, String sortBy, String direction) {
        return build(page, size, sortBy, direction, SORTABLE_CATEGORY_FIELDS, "name");
    }

    /**
     * Paging without sorting, for queries that carry their own ORDER BY.
     *
     * A report is a ranking — "lowest stock first", "best sellers" — and the
     * ordering is what makes the numbers mean anything. Letting a caller re-sort
     * it would not reorder the page, it would change which rows are on the page.
     * Spring Data also appends a {@code Sort} as raw property names, which cannot
     * resolve against a projection's aliases.
     */
    public Pageable forReport(Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? defaultPageSize : size;
        if (pageSize > maxPageSize) pageSize = maxPageSize;

        return PageRequest.of(pageNumber, pageSize, Sort.unsorted());
    }

    /**
     * A pure row limit for the ranked reports that return a list rather than a
     * page — top customers, best sellers. Page zero of {@code limit} rows.
     */
    public Pageable limit(Integer limit) {
        int rows = limit == null || limit < 1 ? 10 : limit;
        if (rows > maxPageSize) rows = maxPageSize;

        return PageRequest.of(0, rows, Sort.unsorted());
    }

    private Pageable build(Integer page, Integer size, String sortBy, String direction,
                           Set<String> allowed, String fallbackField) {

        int pageNumber = page == null || page < 0 ? 0 : page;

        int pageSize = size == null || size < 1 ? defaultPageSize : size;
        // Capped rather than rejected: an oversized page is a client mistake, not
        // a reason to fail the request, but it must not become a table scan.
        if (pageSize > maxPageSize) pageSize = maxPageSize;

        String field = sortBy == null || sortBy.isBlank() ? fallbackField : sortBy.trim();
        if (!allowed.contains(field))
            throw new InvalidInputException("Cannot sort by '" + field + "'. Allowed values: " + allowed);

        Sort.Direction sortDirection = "DESC".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        return PageRequest.of(pageNumber, pageSize, Sort.by(sortDirection, field));
    }
}
