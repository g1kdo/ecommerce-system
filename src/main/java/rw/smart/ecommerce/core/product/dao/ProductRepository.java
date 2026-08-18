package rw.smart.ecommerce.core.product.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import rw.smart.ecommerce.core.product.dao.projection.LowStockProduct;
import rw.smart.ecommerce.core.product.dao.projection.StockShare;
import rw.smart.ecommerce.core.product.model.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * {@link JpaSpecificationExecutor} is what makes the customer catalogue's
 * multi-criteria filtering possible without a combinatorial explosion of derived
 * query methods — see {@code ProductSpecifications}.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * The category is fetched in the same statement; without this every row of a
     * catalogue page would trigger its own lazy-load query.
     */
    @EntityGraph(attributePaths = "category")
    Optional<Product> findWithCategoryById(Long id);

    Optional<Product> findBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    /** Backed by {@code idx_products_name_lower}. */
    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    Page<Product> findByPriceBetween(BigDecimal min, BigDecimal max, Pageable pageable);

    Page<Product> findByCategoryIdAndPriceBetween(Long categoryId, BigDecimal min, BigDecimal max, Pageable pageable);

    /** "New arrivals" strip on the storefront. */
    @EntityGraph(attributePaths = "category")
    List<Product> findTop10ByOrderByCreatedAtDesc();

    long countByCategoryId(Long categoryId);

    @Query("SELECT COALESCE(i.quantity, 0) FROM Inventory i WHERE i.product.id = :productId")
    Optional<Integer> findStockQuantity(Long productId);

    /**
     * Reorder report, driven from {@code Inventory} because that is where the
     * predicate lives — starting from {@code Product} would join every catalogue
     * row only to discard the ones with healthy stock.
     *
     * The count query is supplied explicitly. Spring Data can usually derive one,
     * but not reliably from a multi-alias projection select list, and a wrong
     * count silently corrupts the page metadata rather than failing.
     *
     * Ordering is in the statement, so the {@code Pageable} handed in must be
     * unsorted — see {@code PaginationSupport.forReport}.
     */
    @Query(value = """
            SELECT p.id       AS productId,
                   p.sku      AS sku,
                   p.name     AS name,
                   c.name     AS categoryName,
                   i.quantity AS quantity
            FROM Inventory i
            JOIN i.product p
            JOIN p.category c
            WHERE i.quantity <= :threshold
            ORDER BY i.quantity ASC, p.name ASC
            """,
            countQuery = "SELECT COUNT(i) FROM Inventory i WHERE i.quantity <= :threshold")
    Page<LowStockProduct> findLowStock(int threshold, Pageable pageable);

    /**
     * Bulk price adjustment for a category — a seasonal markdown applied by an
     * administrator.
     *
     * One UPDATE instead of loading every product in the category, mutating each
     * and flushing. {@code flushAutomatically} pushes pending changes to the
     * database before the statement runs so it cannot overwrite them;
     * {@code clearAutomatically} drops the now-stale managed instances, because a
     * bulk update bypasses the persistence context entirely and anything still
     * held there would carry the old price.
     *
     * Callers must evict the product cache — this statement moves prices without
     * any entity callback firing.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.price = ROUND(p.price * :factor, 2) WHERE p.category.id = :categoryId")
    int applyPriceFactorToCategory(Long categoryId, BigDecimal factor);

    /**
     * How a category's stock is distributed across its products.
     *
     * Native for one reason: the share is a window function. Computing it in JPQL
     * would take a second aggregate query for the category total and a join back,
     * or the arithmetic would move into Java over the full result set.
     */
    @Query(value = """
            SELECT p.product_id AS "productId",
                   p.sku        AS "sku",
                   p.name       AS "name",
                   COALESCE(i.quantity, 0) AS "quantity",
                   ROUND(100.0 * COALESCE(i.quantity, 0)
                         / NULLIF(SUM(COALESCE(i.quantity, 0)) OVER (PARTITION BY p.category_id), 0), 2)
                       AS "shareOfCategoryStock"
            FROM products p
            LEFT JOIN inventory i ON i.product_id = p.product_id
            WHERE p.category_id = :categoryId
            ORDER BY "quantity" DESC, p.name ASC
            """, nativeQuery = true)
    List<StockShare> findStockDistributionInCategory(Long categoryId);
}
