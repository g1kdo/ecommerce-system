package rw.smart.ecommerce.core.order.dao.projection;

import java.math.BigDecimal;

/**
 * One day of the sales report, produced by a native query.
 *
 * {@code day} is a formatted string rather than a date type on purpose: the
 * grouping is done by {@code date_trunc} in the database, and returning the
 * already-formatted label avoids a JDBC date conversion in the projection for a
 * value that is only ever rendered.
 */
public interface DailySales {

    String getDay();

    long getOrderCount();

    BigDecimal getRevenue();

    BigDecimal getAverageOrderValue();
}
