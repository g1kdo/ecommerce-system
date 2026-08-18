package rw.smart.ecommerce.core.order.dao.projection;

import java.math.BigDecimal;

/** Units and revenue for one product over a reporting window. */
public interface TopSellingProduct {

    Long getProductId();

    String getProductName();

    String getSku();

    long getUnitsSold();

    BigDecimal getRevenue();
}
