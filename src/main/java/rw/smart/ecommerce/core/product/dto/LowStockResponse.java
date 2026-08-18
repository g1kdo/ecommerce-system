package rw.smart.ecommerce.core.product.dto;

import rw.smart.ecommerce.core.product.dao.projection.LowStockProduct;

/**
 * A reorder-report row.
 *
 * The repository projection is mapped to a record rather than returned directly:
 * a projection is a proxy over a JPA {@code Tuple}, and serializing one exposes
 * the query's shape as the API's shape. This keeps the two free to differ.
 */
public record LowStockResponse(
        Long productId,
        String sku,
        String name,
        String categoryName,
        int quantity) {

    public static LowStockResponse from(LowStockProduct row) {
        return new LowStockResponse(
                row.getProductId(),
                row.getSku(),
                row.getName(),
                row.getCategoryName(),
                row.getQuantity());
    }
}
