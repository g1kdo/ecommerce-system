package rw.smart.ecommerce.core.category.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import rw.smart.ecommerce.core.category.dao.projection.CategoryRevenue;
import rw.smart.ecommerce.core.category.dao.projection.CategorySummary;
import rw.smart.ecommerce.core.category.model.Category;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<Category> findAllByOrderByNameAsc();

    Page<Category> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Guards deletion: the FK is {@code ON DELETE RESTRICT}, so this reports the
     * conflict as a readable 409 instead of letting the driver raise it.
     */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.category.id = :categoryId")
    long countProductsInCategory(Long categoryId);

    /**
     * Catalogue shape per category, in one statement.
     *
     * {@code Category} has no {@code products} collection — adding one would give
     * every category read a lazy list nobody wants — so this uses an explicit
     * entity join with an ON clause. The join is a LEFT one so an empty category
     * still appears, with a zero count and null prices.
     */
    @Query("""
            SELECT c.id   AS categoryId,
                   c.name AS name,
                   COUNT(p.id)  AS productCount,
                   MIN(p.price) AS minPrice,
                   MAX(p.price) AS maxPrice,
                   AVG(p.price) AS averagePrice
            FROM Category c
            LEFT JOIN Product p ON p.category = c
            GROUP BY c.id, c.name
            ORDER BY c.name ASC
            """)
    List<CategorySummary> summarize();

    /**
     * Revenue per category over a window.
     *
     * Native for the aggregate FILTER clauses. The chain
     * category to product to order item to order is all LEFT joined so that a
     * category with no sales still returns a row; but that means order items
     * belonging to excluded orders survive the join with a null order. Without
     * {@code FILTER (WHERE o.order_id IS NOT NULL)} their quantities would be
     * counted, and a cancelled order would show up as revenue.
     */
    @Query(value = """
            SELECT c.category_id AS "categoryId",
                   c.name        AS "name",
                   COUNT(DISTINCT o.order_id) AS "orderCount",
                   COALESCE(SUM(oi.quantity) FILTER (WHERE o.order_id IS NOT NULL), 0) AS "unitsSold",
                   COALESCE(SUM(oi.quantity * oi.unit_price) FILTER (WHERE o.order_id IS NOT NULL), 0) AS "revenue"
            FROM categories c
            LEFT JOIN products p     ON p.category_id = c.category_id
            LEFT JOIN order_items oi ON oi.product_id = p.product_id
            LEFT JOIN orders o       ON o.order_id = oi.order_id
                                    AND o.status <> 'CANCELLED'
                                    AND o.order_date >= :from
                                    AND o.order_date < :to
            GROUP BY c.category_id, c.name
            ORDER BY "revenue" DESC, c.name ASC
            """, nativeQuery = true)
    List<CategoryRevenue> findRevenueByCategory(LocalDateTime from, LocalDateTime to);
}
