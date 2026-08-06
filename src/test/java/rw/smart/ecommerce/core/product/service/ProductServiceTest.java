package rw.smart.ecommerce.core.product.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rw.smart.ecommerce.core.product.cache.ProductCache;
import rw.smart.ecommerce.core.product.dao.ProductDAO;
import rw.smart.ecommerce.core.product.model.Product;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Covers the caching and search/sort logic in ProductService with a mocked DAO,
 * so the assertions are about behaviour rather than about the database.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductDAO productDAO;

    private ProductService productService;

    private Product mouse;
    private Product keyboard;
    private Product pan;

    @BeforeEach
    void setUp() {
        mouse = product(1, "Wireless Mouse", "19.99");
        keyboard = product(2, "Mechanical Keyboard", "59.99");
        pan = product(3, "Stainless Steel Pan", "34.50");
        productService = new ProductService(productDAO, new ProductCache());
    }

    private Product product(int id, String name, String price) {
        Product product = new Product();
        product.setProductId(id);
        product.setName(name);
        product.setSku("SKU-" + id);
        product.setPrice(new BigDecimal(price));
        product.setCategoryId(1);
        return product;
    }

    @Test
    @DisplayName("the cache is warmed once, not on every read")
    void warmsCacheOnlyOnce() throws SQLException {
        when(productDAO.findAll()).thenReturn(List.of(mouse, keyboard, pan));

        assertFalse(productService.isCacheWarm());
        assertEquals(3, productService.getAllProducts().size());
        assertTrue(productService.isCacheWarm());
        assertEquals(3, productService.getAllProducts().size());
        productService.search("mouse");

        verify(productDAO, times(1)).findAll();
    }

    @Test
    @DisplayName("search matches case-insensitively from the cache")
    void searchesCaseInsensitively() throws SQLException {
        when(productDAO.findAll()).thenReturn(List.of(mouse, keyboard, pan));

        List<Product> results = productService.search("MOUSE");

        assertEquals(1, results.size());
        assertEquals("Wireless Mouse", results.get(0).getName());
    }

    @Test
    @DisplayName("a blank search term returns everything")
    void blankSearchReturnsAll() throws SQLException {
        when(productDAO.findAll()).thenReturn(List.of(mouse, keyboard, pan));

        assertEquals(3, productService.search("   ").size());
        assertEquals(3, productService.search(null).size());
    }

    @Test
    @DisplayName("search with no match returns empty, not all products")
    void searchWithNoMatchIsEmpty() throws SQLException {
        when(productDAO.findAll()).thenReturn(List.of(mouse, keyboard, pan));

        assertTrue(productService.search("nonexistent").isEmpty());
    }

    @Test
    void sortsByPriceAscendingAndDescending() {
        List<Product> unsorted = List.of(keyboard, mouse, pan);

        assertEquals(List.of("19.99", "34.50", "59.99"),
                productService.sortByPrice(unsorted, true).stream().map(p -> p.getPrice().toPlainString()).toList());
        assertEquals(List.of("59.99", "34.50", "19.99"),
                productService.sortByPrice(unsorted, false).stream().map(p -> p.getPrice().toPlainString()).toList());
    }

    @Test
    void sortsByNameIgnoringCase() {
        Product lowercase = product(4, "apple stand", "5.00");
        List<Product> sorted = productService.sortByName(List.of(keyboard, lowercase, pan));

        assertEquals(List.of("apple stand", "Mechanical Keyboard", "Stainless Steel Pan"),
                sorted.stream().map(Product::getName).toList());
    }

    @Test
    @DisplayName("create assigns the generated id and writes through to the cache")
    void createWritesThroughToCache() throws SQLException {
        when(productDAO.findAll()).thenReturn(List.of());
        when(productDAO.insert(any(Product.class))).thenReturn(7);
        Product fresh = product(0, "New Gadget", "9.99");

        int newId = productService.createProduct(fresh);

        assertEquals(7, newId);
        assertEquals(7, fresh.getProductId());
        assertSame(fresh, productService.getProduct(7));
        verify(productDAO, never()).findById(anyInt());
    }

    @Test
    @DisplayName("a successful update refreshes the cached entry")
    void updateRefreshesCache() throws SQLException {
        when(productDAO.findAll()).thenReturn(List.of(mouse));
        when(productDAO.update(any(Product.class))).thenReturn(true);

        Product edited = product(1, "Wireless Mouse Pro", "24.99");
        assertTrue(productService.updateProduct(edited));

        assertEquals("Wireless Mouse Pro", productService.getProduct(1).getName());
        verify(productDAO, never()).findById(anyInt());
    }

    @Test
    @DisplayName("a failed update leaves the cache untouched")
    void failedUpdateDoesNotTouchCache() throws SQLException {
        when(productDAO.findAll()).thenReturn(List.of(mouse));
        when(productDAO.update(any(Product.class))).thenReturn(false);

        assertFalse(productService.updateProduct(product(1, "Should Not Apply", "1.00")));

        assertEquals("Wireless Mouse", productService.getProduct(1).getName());
    }

    @Test
    @DisplayName("delete invalidates the entry, so the next read falls back to the DAO")
    void deleteInvalidatesCache() throws SQLException {
        when(productDAO.findAll()).thenReturn(List.of(mouse));
        when(productDAO.delete(1)).thenReturn(true);
        when(productDAO.findById(1)).thenReturn(null);

        assertTrue(productService.deleteProduct(1));

        assertNull(productService.getProduct(1));
        verify(productDAO).findById(1);
    }

    @Test
    @DisplayName("every default-constructed service shares one cache")
    void defaultConstructorSharesASingleCache() throws Exception {
        // Regression: a per-instance cache meant a product created in the product
        // form stayed invisible on the product list, which had warmed its own copy.
        java.lang.reflect.Field cacheField = ProductService.class.getDeclaredField("cache");
        cacheField.setAccessible(true);

        Object listScreenCache = cacheField.get(new ProductService());
        Object productFormCache = cacheField.get(new ProductService());

        assertSame(listScreenCache, productFormCache,
                "screens each build their own ProductService; they must share one cache");
    }

    @Test
    @DisplayName("a product created through one service is visible through another")
    void writeThroughIsVisibleAcrossServices() throws SQLException {
        ProductCache sharedCache = new ProductCache();
        ProductService listScreen = new ProductService(productDAO, sharedCache);
        ProductService productForm = new ProductService(productDAO, sharedCache);

        when(productDAO.findAll()).thenReturn(List.of(mouse));
        assertEquals(1, listScreen.search("").size(), "list screen starts with the stored catalogue");

        when(productDAO.insert(any(Product.class))).thenReturn(9);
        Product fresh = product(0, "Newly Added Gadget", "9.99");
        productForm.createProduct(fresh);

        List<Product> afterCreate = listScreen.search("");
        assertEquals(2, afterCreate.size());
        assertTrue(afterCreate.stream().anyMatch(p -> p.getProductId() == 9),
                "the newly created product must appear on the list screen");
    }

    @Test
    @DisplayName("reloadCache re-reads the catalogue for changes made outside the app")
    void reloadCacheRefetchesFromTheDatabase() throws SQLException {
        when(productDAO.findAll()).thenReturn(List.of(mouse), List.of(mouse, keyboard));

        assertEquals(1, productService.getAllProducts().size());
        productService.reloadCache();

        assertEquals(2, productService.getAllProducts().size());
        verify(productDAO, times(2)).findAll();
    }

    @Test
    @DisplayName("a cache miss falls back to the DAO and caches the result")
    void cacheMissFallsBackToDao() throws SQLException {
        // the fetched product must carry id 99 - the cache keys on the entity's own id
        Product missing = product(99, "Late Arrival", "9.99");
        when(productDAO.findAll()).thenReturn(List.of());
        when(productDAO.findById(99)).thenReturn(missing);

        assertSame(missing, productService.getProduct(99));
        assertSame(missing, productService.getProduct(99));

        verify(productDAO, times(1)).findById(99);
    }
}
