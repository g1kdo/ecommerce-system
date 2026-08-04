package rw.smart.ecommerce.core.product.service;

import rw.smart.ecommerce.core.product.dao.ProductDAO;
import rw.smart.ecommerce.core.product.model.Product;
import rw.smart.ecommerce.core.product.cache.ProductCache;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business logic layer for Products. Wraps ProductDAO and integrates the
 * in-memory ProductCache. Controllers call only this class, never the DAO
 * or JDBC directly (layered architecture constraint).
 */
public class ProductService {

    private final ProductDAO productDAO;
    private final ProductCache cache;

    public ProductService() {
        this(new ProductDAO(), new ProductCache());
    }

    /** Injection point for tests, which supply a mocked DAO and a real cache. */
    public ProductService(ProductDAO productDAO, ProductCache cache) {
        this.productDAO = productDAO;
        this.cache = cache;
    }

    /** Whether reads are currently served from memory — reported in search logs. */
    public boolean isCacheWarm() {
        return cache.isLoaded();
    }

    /**
     * Lazily warms the cache on first access.
     * @throws SQLException
     */
    private  void ensureCacheLoaded() throws SQLException {
        if (!cache.isLoaded())
            cache.loadAll(productDAO.findAll());
    }

    public Product getProduct(int productId) throws SQLException {
        ensureCacheLoaded();
        Product cached = cache.get(productId);

        if (cached != null) return cached;

        // cache miss fallback, go to DB directly
        Product fromDb = productDAO.findById(productId);
        if (fromDb != null) cache.put(fromDb);

        return fromDb;
    }

    public List<Product> getAllProducts() throws SQLException {
        ensureCacheLoaded();
        return cache.getAll();
    }

    /**
     * Case-insensitive search. Served from cache when warm (in-memory
     * substring match); falls back to the indexed SQL query otherwise.
     */
    public List<Product> search(String term) throws SQLException {
        if (term == null || term.isBlank())
            return getAllProducts();

        ensureCacheLoaded();
        String lowerTerm = term.toLowerCase();
        return cache.getAll().stream()
                .filter(p -> p.getName().toLowerCase().contains(lowerTerm))
                .collect(Collectors.toList());
    }

    public List<Product> sortByPrice(List<Product> products, boolean ascending) {
        Comparator<Product> comparator = Comparator.comparing(Product::getPrice);
        if (!ascending) {
            comparator = comparator.reversed();
        }
        return products.stream().sorted(comparator).collect(Collectors.toList());
    }

    public List<Product> sortByName(List<Product> products) {
        return products.stream()
                .sorted(Comparator.comparing(p -> p.getName().toLowerCase()))
                .collect(Collectors.toList());
    }

    /*
     * The mutators warm the cache before writing through. Without that, a write
     * against a cold cache is silently discarded: the entry is put first, then the
     * next read triggers loadAll(), which clears the map to replace it.
     */

    public int createProduct(Product product) throws SQLException {
        ensureCacheLoaded();
        int newId = productDAO.insert(product);
        product.setProductId(newId);
        cache.put(product); // write-through invalidation (new entry)
        return newId;
    }

    public boolean updateProduct(Product product) throws SQLException {
        ensureCacheLoaded();
        boolean success = productDAO.update(product);
        if (success) {
            cache.put(product); // write-through invalidation (refresh entry)
        }
        return success;
    }

    public boolean deleteProduct(int productId) throws SQLException {
        ensureCacheLoaded();
        boolean success = productDAO.delete(productId);
        if (success) {
            cache.invalidate(productId); // write-through invalidation (remove entry)
        }
        return success;
    }
}
