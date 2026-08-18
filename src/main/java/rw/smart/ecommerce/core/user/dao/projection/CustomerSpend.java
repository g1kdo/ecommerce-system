package rw.smart.ecommerce.core.user.dao.projection;

import java.math.BigDecimal;

/**
 * What one customer has spent. Used both for the top-customers ranking and for
 * the lapsed-customer report, which want the same columns under different
 * filters.
 */
public interface CustomerSpend {

    Long getUserId();

    String getFullName();

    String getEmail();

    long getOrderCount();

    BigDecimal getTotalSpent();
}
