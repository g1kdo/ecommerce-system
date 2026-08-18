package rw.smart.ecommerce.core.category.dao.projection;

import java.math.BigDecimal;

/** Sales performance of one category over a reporting window. */
public interface CategoryRevenue {

    Long getCategoryId();

    String getName();

    long getOrderCount();

    long getUnitsSold();

    BigDecimal getRevenue();
}
