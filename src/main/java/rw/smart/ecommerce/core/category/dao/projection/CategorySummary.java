package rw.smart.ecommerce.core.category.dao.projection;

import java.math.BigDecimal;

/**
 * Catalogue shape per category.
 *
 * The price columns are nullable by design — a category with no products has no
 * minimum price, and returning {@code null} says that honestly where a
 * {@code COALESCE} to zero would claim the category sells something free.
 */
public interface CategorySummary {

    Long getCategoryId();

    String getName();

    long getProductCount();

    BigDecimal getMinPrice();

    BigDecimal getMaxPrice();

    Double getAveragePrice();
}
