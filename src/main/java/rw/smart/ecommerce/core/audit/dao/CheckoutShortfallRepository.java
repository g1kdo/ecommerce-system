package rw.smart.ecommerce.core.audit.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import rw.smart.ecommerce.core.audit.dao.projection.MissedDemand;
import rw.smart.ecommerce.core.audit.model.CheckoutShortfall;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CheckoutShortfallRepository extends JpaRepository<CheckoutShortfall, Long> {

    List<CheckoutShortfall> findByUserIdOrderByRecordedAtDesc(Long userId);

    Page<CheckoutShortfall> findByRecordedAtAfter(LocalDateTime since, Pageable pageable);

    long countByProductId(Long productId);

    /**
     * The demand this catalogue failed to serve, worst product first.
     *
     * This is what makes the table worth keeping rather than only logging the
     * failure: a stockout that a customer hit twice is a restocking decision, and
     * it is invisible in the orders table because the order was never written.
     */
    @Query("""
            SELECT s.productId AS productId,
                   COUNT(s.id) AS occurrences,
                   SUM(s.requestedQuantity) AS unitsMissed
            FROM CheckoutShortfall s
            WHERE s.recordedAt >= :since
            GROUP BY s.productId
            ORDER BY SUM(s.requestedQuantity) DESC
            """)
    List<MissedDemand> summarizeMissedDemand(LocalDateTime since, Pageable pageable);
}
