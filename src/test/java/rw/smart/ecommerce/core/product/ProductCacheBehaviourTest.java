package rw.smart.ecommerce.core.product;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import rw.smart.ecommerce.config.CacheConfig;
import rw.smart.ecommerce.core.category.dao.CategoryRepository;
import rw.smart.ecommerce.core.category.dto.CategoryRequest;
import rw.smart.ecommerce.core.category.dto.CategoryResponse;
import rw.smart.ecommerce.core.category.service.CategoryService;
import rw.smart.ecommerce.core.inventory.dao.InventoryRepository;
import rw.smart.ecommerce.core.inventory.service.InventoryService;
import rw.smart.ecommerce.core.product.dao.ProductRepository;
import rw.smart.ecommerce.core.product.dto.ProductRequest;
import rw.smart.ecommerce.core.product.dto.ProductResponse;
import rw.smart.ecommerce.core.product.service.ProductService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Asserts what the cache annotations actually do, by reading the cache directly
 * rather than by timing anything.
 *
 * A timing-based cache test is a flaky test: it passes on a fast machine and
 * fails on a loaded one, and it never says which annotation was wrong. Reading
 * the entry out of the {@link CacheManager} answers the only questions that
 * matter — is the value there, and is it the right one.
 *
 * <h4>Requires PostgreSQL</h4>
 *
 * <pre>createdb -U postgres smart_ecommerce_test_db</pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Cache population, replacement and eviction")
class ProductCacheBehaviourTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    private CategoryResponse category;
    private ProductResponse product;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);

        category = categoryService.create(new CategoryRequest("Cache Fixtures " + tag, "Created by a test"));
        product = productService.create(new ProductRequest(
                "Cached Product " + tag, "Created by a test", new BigDecimal("42.00"), category.id(), 7));

        clearAll();
    }

    @AfterEach
    void tearDown() {
        inventoryRepository.findByProductId(product.id()).ifPresent(inventoryRepository::delete);
        productRepository.deleteById(product.id());
        categoryRepository.deleteById(category.id());
        clearAll();
    }

    @Test
    @DisplayName("@Cacheable populates on the first read and serves the same instance after")
    void findByIdPopulatesTheCache() {
        assertNull(cachedProduct(), "the cache was cleared, so nothing should be there yet");

        ProductResponse first = productService.findById(product.id());
        assertNotNull(cachedProduct(), "the first read should have populated the entry");

        ProductResponse second = productService.findById(product.id());

        // Same object identity, not merely equal: that is only possible if the
        // second call never reached the service body.
        assertSame(first, second, "the second read should have been served from the cache");
    }

    @Test
    @DisplayName("@CachePut replaces the entry with the updated product rather than dropping it")
    void updateReplacesTheCachedEntry() {
        productService.findById(product.id());
        assertEquals("42.00", cachedProduct().price().toPlainString());

        productService.update(product.id(), new ProductRequest(
                "Renamed Product", "Updated by a test", new BigDecimal("55.50"), category.id(), null));

        ProductResponse cached = cachedProduct();

        // The distinction from @CacheEvict: the entry is still here, and it is
        // the new value. An evict would have left null and made the next reader
        // pay for the reload.
        assertNotNull(cached, "@CachePut should leave an entry behind, not remove it");
        assertEquals("Renamed Product", cached.name());
        assertEquals("55.50", cached.price().toPlainString());
    }

    @Test
    @DisplayName("a stock movement evicts the product, because the cached product carries a stock figure")
    void stockChangeEvictsTheProduct() {
        productService.findById(product.id());
        assertEquals(7, cachedProduct().stockQuantity());

        inventoryService.setStock(product.id(), 99);

        assertNull(cachedProduct(), "the cached product still claimed 7 units; it had to go");
        assertEquals(99, productService.findById(product.id()).stockQuantity());
    }

    @Test
    @DisplayName("a stock movement also evicts the catalogue reports that are functions of it")
    void stockChangeEvictsCatalogueReports() {
        productService.findLowStock(1000, 0, 10);
        // The reorder report is not itself cached, but the reports that are
        // derive from the same numbers; check the cache the eviction targets.
        cache(CacheConfig.CATALOGUE_REPORTS).put("stockDistribution:" + category.id(), List.of());
        assertNotNull(cache(CacheConfig.CATALOGUE_REPORTS).get("stockDistribution:" + category.id()));

        inventoryService.setStock(product.id(), 3);

        assertNull(cache(CacheConfig.CATALOGUE_REPORTS).get("stockDistribution:" + category.id()),
                "a stock movement changes the stock distribution report");
    }

    @Test
    @DisplayName("a category write evicts the whole category cache, not one key")
    void categoryWriteEvictsEveryCategoryEntry() {
        categoryService.findAll();
        categoryService.findById(category.id());

        assertNotNull(cache(CacheConfig.CATEGORIES).get("all"));
        assertNotNull(cache(CacheConfig.CATEGORIES).get("id:" + category.id()));

        categoryService.update(category.id(),
                new CategoryRequest("Renamed " + category.name(), "Updated by a test"));

        // allEntries, because the list cached under 'all' contains the renamed
        // category too. Evicting only 'id:<id>' would leave the listing wrong.
        assertNull(cache(CacheConfig.CATEGORIES).get("all"));
        assertNull(cache(CacheConfig.CATEGORIES).get("id:" + category.id()));
    }

    private ProductResponse cachedProduct() {
        Cache.ValueWrapper wrapper = cache(CacheConfig.PRODUCTS).get(product.id());
        return wrapper == null ? null : (ProductResponse) wrapper.get();
    }

    private Cache cache(String name) {
        Cache cache = cacheManager.getCache(name);
        if (cache == null) throw new IllegalStateException("cache not configured: " + name);
        return cache;
    }

    private void clearAll() {
        cacheManager.getCacheNames().forEach(name -> cache(name).clear());
    }
}
