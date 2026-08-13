package rw.smart.ecommerce.core.product.dto;

import java.math.BigDecimal;

/**
 * Multi-criteria catalogue query. Every field is optional — an all-null filter
 * means "the whole catalogue" — and each non-null field narrows the result by
 * one more predicate.
 */
public record ProductFilter(
        String keyword,
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice) {

    public static ProductFilter empty() {
        return new ProductFilter(null, null, null, null);
    }

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }
}
