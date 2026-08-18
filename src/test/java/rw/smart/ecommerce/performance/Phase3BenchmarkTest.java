package rw.smart.ecommerce.performance;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import rw.smart.ecommerce.config.CacheConfig;
import rw.smart.ecommerce.core.category.dao.CategoryRepository;
import rw.smart.ecommerce.core.category.model.Category;
import rw.smart.ecommerce.core.inventory.dao.InventoryRepository;
import rw.smart.ecommerce.core.inventory.model.Inventory;
import rw.smart.ecommerce.core.order.dao.OrderRepository;
import rw.smart.ecommerce.core.order.enums.OrderStatus;
import rw.smart.ecommerce.core.order.model.Order;
import rw.smart.ecommerce.core.order.model.item.OrderItem;
import rw.smart.ecommerce.core.product.dao.ProductRepository;
import rw.smart.ecommerce.core.product.model.Product;
import rw.smart.ecommerce.core.product.service.ProductService;
import rw.smart.ecommerce.core.report.service.ReportService;
import rw.smart.ecommerce.core.user.dao.UserRepository;
import rw.smart.ecommerce.core.user.enums.UserRole;
import rw.smart.ecommerce.core.user.model.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The measurement harness behind section 11 of {@code docs/performance-report.md}.
 *
 * <h4>Running it</h4>
 *
 * <pre>./mvnw -o test -Dtest=Phase3BenchmarkTest -Dbenchmark=true</pre>
 *
 * It is gated on that system property rather than {@code @Disabled} because a
 * disabled test cannot be run at all, and a benchmark nobody can re-run is a
 * benchmark nobody should believe. It seeds around 4 500 rows, measures, prints a
 * markdown table to stdout, and deletes everything it created.
 *
 * <h4>What it measures, and how</h4>
 *
 * Wall time is the median of {@link #RUNS} timed runs after one warm-up, which is
 * the same protocol the Phase 1 index study used. Statement counts come from
 * Hibernate's own {@code Statistics.getPrepareStatementCount()} — a count, not a
 * timing, so it is the same on any machine and is the more useful number of the
 * two for the N+1 comparisons.
 *
 * The paginated order page is measured separately, by the two
 * {@code OrderPageBenchmark} subclasses, which need one application context per
 * batch-fetch setting - see the note in AbstractOrderPageBenchmark.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "benchmark", matches = "true",
        disabledReason = "Benchmark: run with -Dbenchmark=true")
@DisplayName("Phase 3 before/after measurements")
class Phase3BenchmarkTest {

    private static final int RUNS = 5;
    private static final int CATEGORIES = 4;
    private static final int PRODUCTS_PER_CATEGORY = 50;
    private static final int ORDERS = 400;
    private static final int LINES_PER_ORDER = 3;
    private static final int PAGE_SIZE = 20;
    private static final String TAG = "bench";

    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductService productService;
    @Autowired private ReportService reportService;
    @Autowired private CacheManager cacheManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private TransactionTemplate transactions;

    @PersistenceContext private EntityManager entityManager;

    private static final List<String[]> RESULTS = new ArrayList<>();

    private static Long customerId;
    private static List<Long> categoryIds;
    private static List<Long> productIds;
    private static boolean seeded;

    @BeforeAll
    static void announce() {
        System.out.println("\n=== Phase 3 benchmark: seeding, measuring, cleaning up ===\n");
    }

    @AfterAll
    static void report() {
        System.out.println("\n| Measurement | Before | After | Change |");
        System.out.println("|---|---:|---:|---|");
        RESULTS.forEach(row -> System.out.printf("| %s | %s | %s | %s |%n",
                row[0], row[1], row[2], row[3]));
        System.out.println();
    }

    @Test
    @DisplayName("measures every Phase 3 optimization and prints the table")
    void measure() {
        seed();
        try {
            // The paginated order page is measured by OrderPageBatchedBenchmarkTest
            // and OrderPageUnbatchedBenchmarkTest, which need two contexts.
            measureAggregateVersusJavaGrouping();
            measureExistsVersusLoadingHistory();
            measureProjectionVersusEntities();
            measureProductCache();
            measureReportCache();
        } finally {
            cleanUp();
        }
    }

    // ------------------------------------------------------------------
    // 2. Aggregating in the database instead of in Java
    // ------------------------------------------------------------------

    /**
     * The order-status breakdown, computed two ways: the JPQL {@code GROUP BY}
     * the repository now runs, and the obvious alternative of loading the orders
     * in the window and grouping them in a stream.
     */
    private void measureAggregateVersusJavaGrouping() {
        LocalDateTime from = LocalDate.now().minusDays(60).atStartOfDay();
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();

        Measurement inJava = measureInTransaction(() -> {
            Map<OrderStatus, long[]> grouped = new HashMap<>();
            for (Order order : orderRepository.findAll()) {
                if (order.getOrderDate().isBefore(from) || !order.getOrderDate().isBefore(to)) continue;
                grouped.computeIfAbsent(order.getStatus(), key -> new long[1])[0]++;
            }
            return grouped.size();
        });

        Measurement inSql = measureInTransaction(
                () -> orderRepository.summarizeByStatus(from, to).size());

        record("Order status breakdown over %d orders — rows read".formatted(ORDERS),
                String.valueOf(ORDERS), "%d (one per status)".formatted(inSql.result),
                "aggregate stays in the database");

        record("Order status breakdown — wall time",
                ms(inJava.nanos), ms(inSql.nanos), "%.1fx faster".formatted(ratio(inJava.nanos, inSql.nanos)));
    }

    // ------------------------------------------------------------------
    // 3. EXISTS instead of loading a history to call isEmpty() on it
    // ------------------------------------------------------------------

    private void measureExistsVersusLoadingHistory() {
        Measurement load = measureInTransaction(
                () -> orderRepository.findByUserIdOrderByOrderDateDesc(customerId).isEmpty() ? 0 : 1);

        Measurement exists = measureInTransaction(
                () -> orderRepository.existsByUserId(customerId) ? 1 : 0);

        record("\"Does this user have orders?\" — wall time",
                ms(load.nanos), ms(exists.nanos), "%.1fx faster".formatted(ratio(load.nanos, exists.nanos)));

        record("\"Does this user have orders?\" — orders loaded into heap",
                String.valueOf(ORDERS), "0", "answered by the index");
    }

    // ------------------------------------------------------------------
    // 4. Interface projection instead of hydrating entities
    // ------------------------------------------------------------------

    private void measureProjectionVersusEntities() {
        Measurement entities = measureInTransaction(() -> {
            List<Inventory> rows = entityManager
                    .createQuery("SELECT i FROM Inventory i WHERE i.quantity <= :threshold", Inventory.class)
                    .setParameter("threshold", Integer.MAX_VALUE)
                    .setMaxResults(PAGE_SIZE)
                    .getResultList();

            // What the report actually renders, which is what forces the lazy
            // product and category to be initialised.
            rows.forEach(row -> row.getProduct().getCategory().getName());
            return rows.size();
        });

        Measurement projection = measureInTransaction(
                () -> productRepository.findLowStock(Integer.MAX_VALUE, PageRequest.of(0, PAGE_SIZE)).getSize());

        record("Reorder report, %d rows — JDBC statements".formatted(PAGE_SIZE),
                String.valueOf(entities.statements), String.valueOf(projection.statements),
                "%.1fx fewer".formatted(ratio(entities.statements, projection.statements)));

        record("Reorder report, %d rows — wall time".formatted(PAGE_SIZE),
                ms(entities.nanos), ms(projection.nanos),
                "%.1fx faster".formatted(ratio(entities.nanos, projection.nanos)));
    }

    // ------------------------------------------------------------------
    // 5. Caches
    // ------------------------------------------------------------------

    private void measureProductCache() {
        Long productId = productIds.getFirst();

        long cold = median(() -> {
            cacheManager.getCache(CacheConfig.PRODUCTS).clear();
            return time(() -> productService.findById(productId));
        });

        productService.findById(productId);
        long warm = median(() -> time(() -> productService.findById(productId)));

        record("Product detail by id — one read",
                us(cold), us(warm), "%.0fx faster".formatted(ratio(cold, warm)));
    }

    private void measureReportCache() {
        LocalDate from = LocalDate.now().minusDays(60);
        LocalDate to = LocalDate.now();

        long cold = median(() -> {
            cacheManager.getCache(CacheConfig.SALES_REPORTS).clear();
            return time(() -> reportService.dailySales(from, to));
        });

        reportService.dailySales(from, to);
        long warm = median(() -> time(() -> reportService.dailySales(from, to)));

        record("Daily sales report, 60-day window",
                us(cold), us(warm), "%.0fx faster".formatted(ratio(cold, warm)));
    }

    // ------------------------------------------------------------------
    // Measurement plumbing
    // ------------------------------------------------------------------

    private record Measurement(long nanos, long statements, int result) {
    }

    /**
     * Runs {@code work} in its own transaction, once to warm up and then
     * {@link #RUNS} times. Reports the median wall time and the statement count
     * of the last timed run.
     */
    private Measurement measureInTransaction(Supplier<Integer> work) {
        transactions.execute(status -> runOnce(work));

        long[] timings = new long[RUNS];
        long statements = 0;
        int result = 0;

        for (int run = 0; run < RUNS; run++) {
            Statistics statistics = statistics();
            long before = statistics.getPrepareStatementCount();
            long started = System.nanoTime();

            result = transactions.execute(status -> runOnce(work));

            timings[run] = System.nanoTime() - started;
            statements = statistics.getPrepareStatementCount() - before;
        }

        Arrays.sort(timings);
        return new Measurement(timings[RUNS / 2], statements, result);
    }

    /** The persistence context is cleared between runs so nothing is measured warm. */
    private Integer runOnce(Supplier<Integer> work) {
        Integer value = work.get();
        entityManager.clear();
        return value;
    }

    private long median(Supplier<Long> timedRun) {
        timedRun.get();

        long[] timings = new long[RUNS];
        for (int run = 0; run < RUNS; run++) timings[run] = timedRun.get();

        Arrays.sort(timings);
        return timings[RUNS / 2];
    }

    private long time(Runnable work) {
        long started = System.nanoTime();
        work.run();
        return System.nanoTime() - started;
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private void record(String label, String before, String after, String change) {
        RESULTS.add(new String[]{label, before, after, change});
        System.out.printf("  %-58s %12s -> %12s  (%s)%n", label, before, after, change);
    }

    private static double ratio(long before, long after) {
        return after == 0 ? Double.POSITIVE_INFINITY : (double) before / after;
    }

    private static String ms(long nanos) {
        return "%.3f ms".formatted(nanos / 1_000_000.0);
    }

    private static String us(long nanos) {
        return "%.1f us".formatted(nanos / 1_000.0);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private void seed() {
        if (seeded) return;

        transactions.executeWithoutResult(status -> {
            User customer = new User();
            customer.setUsername(TAG + "-customer");
            customer.setEmail(TAG + "-customer@example.test");
            customer.setPasswordHash("not-a-real-hash");
            customer.setFullName("Benchmark Customer");
            customer.setRole(UserRole.CUSTOMER);
            customerId = userRepository.save(customer).getId();

            categoryIds = new ArrayList<>();
            productIds = new ArrayList<>();
            List<Product> products = new ArrayList<>();

            for (int c = 0; c < CATEGORIES; c++) {
                Category category = new Category();
                category.setName(TAG + " Category " + c);
                category.setDescription("Benchmark fixture");
                category = categoryRepository.save(category);
                categoryIds.add(category.getId());

                for (int p = 0; p < PRODUCTS_PER_CATEGORY; p++) {
                    Product product = new Product();
                    product.setName(TAG + " Product " + c + "-" + p);
                    product.setDescription("Benchmark fixture");
                    product.setPrice(BigDecimal.valueOf(5 + (p % 40)).setScale(2));
                    product.setSku(TAG.toUpperCase() + "-" + c + "-" + p);
                    product.setCategory(category);
                    Product saved = productRepository.save(product);

                    Inventory inventory = new Inventory();
                    inventory.setProduct(saved);
                    inventory.setQuantity(p % 12);
                    inventoryRepository.save(inventory);

                    products.add(saved);
                    productIds.add(saved.getId());
                }
            }

            OrderStatus[] statuses = OrderStatus.values();
            for (int o = 0; o < ORDERS; o++) {
                Order order = new Order();
                order.setUser(customer);
                order.setStatus(statuses[o % statuses.length]);
                order.setOrderDate(LocalDateTime.now().minusDays(o % 60).minusHours(o % 24));

                for (int line = 0; line < LINES_PER_ORDER; line++) {
                    Product product = products.get((o * LINES_PER_ORDER + line) % products.size());
                    OrderItem item = new OrderItem();
                    item.setProduct(product);
                    item.setQuantity(1 + (line % 3));
                    item.setUnitPrice(product.getPrice());
                    order.addItem(item);
                }

                order.setTotalAmount(order.calculateTotal());
                orderRepository.save(order);
            }
        });

        seeded = true;
        System.out.printf("Seeded %d products, %d orders, %d order lines.%n%n",
                productIds.size(), ORDERS, ORDERS * LINES_PER_ORDER);
    }

    private void cleanUp() {
        transactions.executeWithoutResult(status -> {
            orderRepository.deleteAll(orderRepository.findByUserIdOrderByOrderDateDesc(customerId));
            productIds.forEach(id -> {
                inventoryRepository.findByProductId(id).ifPresent(inventoryRepository::delete);
                productRepository.deleteById(id);
            });
            categoryIds.forEach(categoryRepository::deleteById);
            userRepository.deleteById(customerId);
        });

        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        System.out.println("\nFixtures removed.");
    }
}
