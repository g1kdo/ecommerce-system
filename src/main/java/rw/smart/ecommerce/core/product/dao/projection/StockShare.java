package rw.smart.ecommerce.core.product.dao.projection;

import java.math.BigDecimal;

/**
 * How much of a category's stock sits on one product. The percentage is computed
 * by a window function in the database, which is why the query behind this is
 * native — JPQL has no {@code OVER (PARTITION BY ...)}.
 */
public interface StockShare {

    Long getProductId();

    String getSku();

    String getName();

    int getQuantity();

    BigDecimal getShareOfCategoryStock();
}
