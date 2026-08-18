package rw.smart.ecommerce.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
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
 * spec, because they have genuinely different tolerances for staleness — a
 * category list can be minutes old without anyone noticing, a product's stock
 * level cannot. Each one is bounded and each one expires; an unbounded cache is
 * a memory leak that only shows up under load.
 *
 * <h4>Two ways a cache here stays correct</h4>
 *
 * The catalogue caches are correct <em>by eviction</em>. Writes evict or replace
 * their entry explicitly (see the {@code @CacheEvict} and {@code @CachePut}
 * annotations in the services); the TTL is only a backstop for changes made
 * outside this application.
 *
 * The sales report cache is correct <em>by expiry</em>, deliberately. Every
 * order placed changes the numbers in a sales report, so evicting on writes
 * would mean evicting on every checkout and the cache would never be warm on a
 * busy day. A revenue figure is a snapshot with a stated age, and five minutes
 * is that statement. The catalogue-shaped reports are in a separate cache
 * precisely so they can keep the eviction rule the sales reports cannot.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PRODUCTS = "products";
    public static final String CATEGORIES = "categories";
    public static final String REVIEW_SUMMARIES = "reviewSummaries";
    public static final String PROFILES = "profiles";
    public static final String SALES_REPORTS = "salesReports";
    public static final String CATALOGUE_REPORTS = "catalogueReports";

    /**
     * Wrapped in a {@link TransactionAwareCacheManagerProxy} so that every
     * {@code put} and {@code evict} is deferred until the surrounding transaction
     * commits.
     *
     * Without it the cache is written at method return, which is before commit.
     * A constraint violation raised at flush would then leave the cache holding a
     * value the database rejected — and {@code @CachePut} makes that worse than
     * {@code @CacheEvict} did, because a stale eviction only costs a miss whereas
     * a stale put serves data that was never persisted. Reads still go straight
     * through; only writes wait.
     */
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager delegate = new SimpleCacheManager();
        delegate.setCaches(List.of(
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
                cache(REVIEW_SUMMARIES, 5_000, Duration.ofMinutes(5)),

                // Profiles are read on every administrative screen that shows a
                // name against an order. Bounded well below the user count: this
                // is a working set, not a copy of the table.
                cache(PROFILES, 1_000, Duration.ofMinutes(10)),

                // Aggregations over the whole order history. Small, because each
                // entry is one report over one window and there are only so many
                // windows anyone asks for.
                cache(SALES_REPORTS, 200, Duration.ofMinutes(5)),

                // Catalogue shape rather than sales: category summaries, stock
                // distribution. Longer lived because only an administrator's own
                // writes change them, and those evict it.
                cache(CATALOGUE_REPORTS, 200, Duration.ofMinutes(30))));

        // The delegate is not the bean Spring returns, so its own
        // afterPropertiesSet() never runs and the caches would stay unregistered.
        delegate.initializeCaches();

        return new TransactionAwareCacheManagerProxy(delegate);
    }

    private CaffeineCache cache(String name, int maximumSize, Duration ttl) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .build());
    }
}
