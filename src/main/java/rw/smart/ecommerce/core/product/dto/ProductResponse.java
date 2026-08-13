package rw.smart.ecommerce.core.product.dto;

import rw.smart.ecommerce.core.category.dto.CategoryResponse;
import rw.smart.ecommerce.core.product.model.Product;

import java.math.BigDecimal;

/**
 * Catalogue projection. {@code createdAt} is already an ISO-8601 string so the
 * same record serves both REST (Jackson) and GraphQL without a custom scalar.
 */
public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String sku,
        CategoryResponse category,
        Integer stockQuantity,
        String createdAt) {

    /**
     * Stock is passed in rather than navigated to: it lives in a separate table
     * and is fetched in bulk by the service, which avoids an N+1 query per row.
     */
    public static ProductResponse from(Product product, Integer stockQuantity) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getSku(),
                CategoryResponse.from(product.getCategory()),
                stockQuantity == null ? 0 : stockQuantity,
                product.getCreatedAt() == null ? null : product.getCreatedAt().toString());
    }
}
