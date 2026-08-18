package rw.smart.ecommerce.core.user.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import rw.smart.ecommerce.core.order.enums.OrderStatus;
import rw.smart.ecommerce.core.user.dao.projection.CustomerSpend;
import rw.smart.ecommerce.core.user.model.User;
import rw.smart.ecommerce.core.user.enums.UserRole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    Page<User> findByRole(UserRole role, Pageable pageable);

    Page<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String fullName, String email, Pageable pageable);

    long countByRole(UserRole role);

    /** New sign-ups for the registrations report. */
    Page<User> findByCreatedAtAfter(LocalDateTime since, Pageable pageable);

    /**
     * Highest-spending customers.
     *
     * Driven from {@code Order} rather than {@code User} so the join is an inner
     * one: a customer who has never ordered has no place in a spend ranking, and
     * starting from {@code User} would need an extra {@code HAVING} to exclude
     * them. Cancelled orders are excluded — they were never paid for.
     *
     * {@code Pageable} supplies the limit; pass it unsorted, because the ordering
     * that makes this a ranking is already in the statement.
     */
    @Query("""
            SELECT u.id        AS userId,
                   u.fullName  AS fullName,
                   u.email     AS email,
                   COUNT(o.id) AS orderCount,
                   SUM(o.totalAmount) AS totalSpent
            FROM Order o
            JOIN o.user u
            WHERE o.status <> :excluded
            GROUP BY u.id, u.fullName, u.email
            ORDER BY SUM(o.totalAmount) DESC
            """)
    List<CustomerSpend> findTopCustomers(OrderStatus excluded, Pageable pageable);

    /**
     * Customers who have bought before but not since {@code since} — the list a
     * retention campaign is built from.
     *
     * Native because of the aggregate {@code FILTER} clause. The same customer's
     * lifetime totals and their recent activity have to be computed from one pass
     * over the join under two different predicates; expressing that in JPQL means
     * either two queries or a correlated subquery per row.
     */
    @Query(value = """
            SELECT u.user_id   AS "userId",
                   u.full_name AS "fullName",
                   u.email     AS "email",
                   COUNT(o.order_id)               AS "orderCount",
                   COALESCE(SUM(o.total_amount), 0) AS "totalSpent"
            FROM users u
            JOIN orders o ON o.user_id = u.user_id AND o.status <> 'CANCELLED'
            GROUP BY u.user_id, u.full_name, u.email
            HAVING COUNT(o.order_id) FILTER (WHERE o.order_date >= :since) = 0
            ORDER BY "totalSpent" DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<CustomerSpend> findLapsedCustomers(LocalDateTime since, int limit);
}
