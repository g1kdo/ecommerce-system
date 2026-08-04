package rw.smart.ecommerce.core.product.cache;

import rw.smart.ecommerce.core.product.model.Product;

import java.util.*;

/**
 * In-memory cache for Products.
 *
 * DSA choice:
 *  - HashMap<Integer, Product> gives O(1) lookup by product_id, which is the
 *    dominant access pattern (product detail view, cart line lookups).
 *  - The backing values are also exposed as a List for iteration-based
 *    operations (search, sort, pagination) where key-based lookup isn't needed.
 *
 * Invalidation policy: write-through. Every mutating call to ProductService
 * (insert/update/delete) updates or removes the corresponding cache entry
 * in the same method before returning, so a cache hit can never be stale.
 * No TTL/expiry is used — this is a correctness-first policy suited to a
 * single-instance app, not a distributed cache.
 */
public class ProductCache {

    private final Map<Integer, Product> cache = new HashMap<>();
    private boolean loaded = false;

    public boolean isLoaded() {
        return loaded;
    }

    public void loadAll(Collection<Product> products) {
        cache.clear();
        for (Product prod : products) {
            cache.put(prod.getProductId(), prod);
        }
        loaded = true;
    }

    public Product get(int productId) {
        return cache.get(productId);
    }

    public List<Product> getAll() {
        return new ArrayList<>(cache.values());
    }

    /**
     * Called on insert/update — write-through invalidation.
     * @param product
     */
    public void put(Product product) {
        cache.put(product.getProductId(), product);
    }

    /**
     * Called on delete — write-through invalidation.
     * @param productId
     */
    public void invalidate(int productId) {
        cache.remove(productId);
    }

    public  void clear() {
        cache.clear();
        loaded = false;
    }
}
