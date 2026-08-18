package rw.smart.ecommerce.core.order.dao.projection;

import rw.smart.ecommerce.core.order.enums.OrderStatus;

import java.math.BigDecimal;

/**
 * One row of the order-status breakdown.
 *
 * A closed interface projection rather than a DTO constructor expression: the
 * aggregate is never loaded as an entity, so Hibernate selects exactly these
 * five columns and nothing else. A {@code List<Order>} counted in Java would
 * read every row of the table to produce the same numbers.
 */
public interface OrderStatusSummary {

    OrderStatus getStatus();

    long getOrderCount();

    BigDecimal getRevenue();
}
