package rw.smart.ecommerce.core.product.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import rw.smart.ecommerce.core.product.model.Product;

import java.math.BigDecimal;
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

    @Query("SELECT COALESCE(i.quantity, 0) FROM Inventory i WHERE i.product.id = :productId")
    Optional<Integer> findStockQuantity(Long productId);
}
