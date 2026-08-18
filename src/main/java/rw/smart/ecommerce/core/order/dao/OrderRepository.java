package rw.smart.ecommerce.core.order.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import rw.smart.ecommerce.core.order.dao.projection.DailySales;
import rw.smart.ecommerce.core.order.dao.projection.OrderStatusSummary;
import rw.smart.ecommerce.core.order.model.Order;
import rw.smart.ecommerce.core.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Note what is deliberately absent: an {@code @EntityGraph} on the paginated
 * finders. Fetch-joining {@code items} into a query that also carries
 * LIMIT/OFFSET makes the page wrong, and Hibernate's fallback is to read every
 * matching row and paginate in heap. The paginated methods therefore leave the
 * collection lazy and rely on {@code hibernate.default_batch_fetch_size}, which
 * resolves a whole page of orders' lines in one extra statement instead of one
 * statement per order.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Order detail always needs its lines and the products they point at. */
    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    Optional<Order> findWithItemsById(Long id);

    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    List<Order> findByUserIdOrderByOrderDateDesc(Long userId);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    Page<Order> findByOrderDateBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    long countByStatus(OrderStatus status);

    /**
     * Replaces loading a user's entire order history — every line and every
     * product — only to call {@code isEmpty()} on it. This is one indexed EXISTS.
     */
    boolean existsByUserId(Long userId);

    /**
     * Revenue over a window. The {@code COALESCE} matters: {@code SUM} over no
     * rows is null, and a report that renders "no sales yet" as a blank instead
     * of a zero reads as a bug in the report.
     */
    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0)
            FROM Order o
            WHERE o.status <> :excluded
              AND o.orderDate >= :from
              AND o.orderDate < :to
            """)
    BigDecimal sumRevenueBetween(OrderStatus excluded, LocalDateTime from, LocalDateTime to);

    /**
     * Order counts and value grouped by status.
     *
     * One aggregate statement in place of five {@code countByStatus} round trips
     * that still could not produce the revenue column.
     */
    @Query("""
            SELECT o.status AS status,
                   COUNT(o.id) AS orderCount,
                   COALESCE(SUM(o.totalAmount), 0) AS revenue
            FROM Order o
            WHERE o.orderDate >= :from AND o.orderDate < :to
            GROUP BY o.status
            ORDER BY o.status ASC
            """)
    List<OrderStatusSummary> summarizeByStatus(LocalDateTime from, LocalDateTime to);

    /**
     * Sales per day.
     *
     * Native because the grouping key is {@code date_trunc}. JPQL can extract a
     * year, a month or a day part, but it cannot truncate a timestamp to a day
     * boundary; grouping on three extracted parts instead produces a composite
     * key the projection would then have to reassemble.
     */
    @Query(value = """
            SELECT to_char(date_trunc('day', o.order_date), 'YYYY-MM-DD') AS "day",
                   COUNT(*) AS "orderCount",
                   COALESCE(SUM(o.total_amount), 0) AS "revenue",
                   COALESCE(AVG(o.total_amount), 0) AS "averageOrderValue"
            FROM orders o
            WHERE o.status <> 'CANCELLED'
              AND o.order_date >= :from
              AND o.order_date < :to
            GROUP BY date_trunc('day', o.order_date)
            ORDER BY date_trunc('day', o.order_date) ASC
            """, nativeQuery = true)
    List<DailySales> findDailySales(LocalDateTime from, LocalDateTime to);
}
