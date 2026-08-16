package rw.smart.ecommerce.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Read caches for the endpoints that are hit hardest and change least.
 *
 * This replaces the hand-rolled {@code ProductCache} from Phase 1, which was a
 * static map with no bound, no expiry, and no thread safety.
 *
 * Every cache is registered individually rather than through a single global
 * spec, because the three have genuinely different tolerances for staleness —
 * a category list can be minutes old without anyone noticing, a product's stock
 * level cannot. Each one is bounded and each one expires; an unbounded cache is
 * a memory leak that only shows up under load.
 *
 * Correctness does not rest on the TTLs. Writes evict explicitly (see the
 * {@code @CacheEvict} annotations in the services); expiry is the backstop for
 * changes made outside this application.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PRODUCTS = "products";
    public static final String CATEGORIES = "categories";
    public static final String REVIEW_SUMMARIES = "reviewSummaries";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                // Product detail embeds a live stock figure, so this is the cache
                // with the least room for drift. Every stock movement evicts it;
                // the short TTL only covers writes made outside the application.
                cache(PRODUCTS, 2_000, Duration.ofSeconds(60)),

                // Categories change a handful of times a year and are read on
                // every catalogue page.
                cache(CATEGORIES, 200, Duration.ofMinutes(30)),

                // A MongoDB $group over every review of a product - by far the
                // most expensive read in the system, and one where a rating
                // average lagging by a minute is invisible to the user.
                cache(REVIEW_SUMMARIES, 5_000, Duration.ofMinutes(5))));

        return manager;
    }

    private CaffeineCache cache(String name, int maximumSize, Duration ttl) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .build());
    }
}
