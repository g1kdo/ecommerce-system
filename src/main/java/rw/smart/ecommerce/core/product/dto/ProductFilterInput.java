package rw.smart.ecommerce.core.product.dto;

import java.math.BigDecimal;

/**
 * Binds the {@code ProductFilterInput} GraphQL input object. It carries the
 * paging and sorting arguments alongside the filter criteria because GraphQL has
 * no query-string equivalent of REST's request parameters.
 */
public record ProductFilterInput(
        String keyword,
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer page,
        Integer size,
        String sortBy,
        String direction) {

    /** Used when the client omits the whole argument. */
    public static ProductFilterInput defaults() {
        return new ProductFilterInput(null, null, null, null, 0, 20, "id", "ASC");
    }
}
