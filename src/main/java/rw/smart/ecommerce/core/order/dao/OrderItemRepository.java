package rw.smart.ecommerce.core.order.dao;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import rw.smart.ecommerce.core.order.dao.projection.RelatedProduct;
import rw.smart.ecommerce.core.order.dao.projection.TopSellingProduct;
import rw.smart.ecommerce.core.order.enums.OrderStatus;
import rw.smart.ecommerce.core.order.model.item.OrderItem;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    /** Blocks product deletion while historical orders still reference it. */
    boolean existsByProductId(Long productId);

    long countByProductId(Long productId);

    /** Lines for a whole page of orders in one statement rather than one each. */
    List<OrderItem> findByOrderIdIn(Collection<Long> orderIds);

    /**
     * Lifetime units sold for one product. Cancelled orders are excluded, so this
     * is a sales figure rather than a basket figure.
     */
    @Query("""
            SELECT COALESCE(SUM(i.quantity), 0)
            FROM OrderItem i
            WHERE i.product.id = :productId AND i.order.status <> :excluded
            """)
    long sumUnitsSold(Long productId, OrderStatus excluded);

    /**
     * Best sellers over a window.
     *
     * Revenue is summed from the line's own {@code unitPrice}, never the
     * product's current price. That is the whole reason the snapshot exists: a
     * markdown applied today must not rewrite what last quarter earned.
     *
     * The ranking is in the statement, so the {@code Pageable} handed in is a
     * limit and must be unsorted.
     */
    @Query("""
            SELECT p.id   AS productId,
                   p.name AS productName,
                   p.sku  AS sku,
                   SUM(i.quantity) AS unitsSold,
                   SUM(i.unitPrice * i.quantity) AS revenue
            FROM OrderItem i
            JOIN i.product p
            JOIN i.order o
            WHERE o.status <> :excluded
              AND o.orderDate >= :from
              AND o.orderDate < :to
            GROUP BY p.id, p.name, p.sku
            ORDER BY SUM(i.quantity) DESC
            """)
    List<TopSellingProduct> findTopSelling(OrderStatus excluded, LocalDateTime from,
                                           LocalDateTime to, Pageable pageable);

    /**
     * What else people bought in the same order.
     *
     * Native because it is a self-join on {@code order_items} with no association
     * to navigate. JPQL can approximate it with a subquery over order ids, but
     * that plans as a semi-join per candidate row where the self-join is a single
     * hash join, and the row limit would have to become a {@code Pageable} on a
     * query whose ordering is an aggregate of that join.
     */
    @Query(value = """
            SELECT p.product_id AS "productId",
                   p.name       AS "productName",
                   COUNT(DISTINCT other.order_id) AS "timesBoughtTogether"
            FROM order_items anchor
            JOIN order_items other ON other.order_id = anchor.order_id
                                  AND other.product_id <> anchor.product_id
            JOIN products p ON p.product_id = other.product_id
            JOIN orders o   ON o.order_id = anchor.order_id AND o.status <> 'CANCELLED'
            WHERE anchor.product_id = :productId
            GROUP BY p.product_id, p.name
            ORDER BY "timesBoughtTogether" DESC, p.name ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<RelatedProduct> findBoughtTogetherWith(Long productId, int limit);
}
