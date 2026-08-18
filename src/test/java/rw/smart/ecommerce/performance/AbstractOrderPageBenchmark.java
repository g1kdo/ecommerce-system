package rw.smart.ecommerce.performance;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;
import rw.smart.ecommerce.core.category.dao.CategoryRepository;
import rw.smart.ecommerce.core.category.model.Category;
import rw.smart.ecommerce.core.inventory.dao.InventoryRepository;
import rw.smart.ecommerce.core.inventory.model.Inventory;
import rw.smart.ecommerce.core.order.dao.OrderRepository;
import rw.smart.ecommerce.core.order.dto.OrderResponse;
import rw.smart.ecommerce.core.order.model.Order;
import rw.smart.ecommerce.core.order.model.item.OrderItem;
import rw.smart.ecommerce.core.product.dao.ProductRepository;
import rw.smart.ecommerce.core.product.model.Product;
import rw.smart.ecommerce.core.user.dao.UserRepository;
import rw.smart.ecommerce.core.user.enums.UserRole;
import rw.smart.ecommerce.core.user.model.User;

import jakarta.persistence.EntityManagerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Measures one thing: what a page of orders costs when each response walks its
 * lines and each line's product.
 *
 * The two subclasses differ only in {@code hibernate.default_batch_fetch_size},
 * and they are two classes rather than two runs of one because that setting is
 * fixed when the {@code SessionFactory} is built. The in-session equivalent,
 * {@code Session.setFetchBatchSize}, does not affect the collection and proxy
 * loads this page performs — it was tried first, produced an identical statement
 * count on both sides, and was discarded rather than reported. Two contexts cost
 * an extra application start; a measurement that silently compares a setting
 * against itself costs more than that.
 *
 * Statement counts come from Hibernate's own statistics. They are the number
 * worth reading here: unlike a timing they do not depend on the machine, and
 * an N+1 is a statement-count problem before it is a latency problem.
 */
@EnabledIfSystemProperty(named = "benchmark", matches = "true",
        disabledReason = "Benchmark: run with -Dbenchmark=true")
abstract class AbstractOrderPageBenchmark {

    private static final int RUNS = 5;
    private static final int ORDERS = 40;
    private static final int LINES_PER_ORDER = 3;
    private static final int PRODUCTS = 30;
    private static final int PAGE_SIZE = 20;

    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private TransactionTemplate transactions;

    private Long customerId;
    private Long categoryId;
    private List<Long> productIds;

    /** What this configuration is called in the report. */
    protected abstract String label();

    @Test
    void measureOrderPage() {
        seed();
        try {
            long[] timings = new long[RUNS];
            long statements = 0;

            // One warm-up, then RUNS timed runs, median reported - the same
            // protocol as the Phase 1 index study.
            readPage();

            for (int run = 0; run < RUNS; run++) {
                Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
                long before = statistics.getPrepareStatementCount();
                long started = System.nanoTime();

                readPage();

                timings[run] = System.nanoTime() - started;
                statements = statistics.getPrepareStatementCount() - before;
            }

            Arrays.sort(timings);

            System.out.printf("%nBENCHMARK order-page [%s]: %d JDBC statements, median %.3f ms "
                            + "for %d orders x %d lines%n%n",
                    label(), statements, timings[RUNS / 2] / 1_000_000.0, PAGE_SIZE, LINES_PER_ORDER);

        } finally {
            cleanUp();
        }
    }

    /** Exactly what {@code OrderServiceImpl.findByUser(...)} does for one page. */
    private void readPage() {
        transactions.executeWithoutResult(status -> orderRepository
                .findByUserId(customerId, PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "orderDate")))
                .map(OrderResponse::from)
                .getContent());
    }

    private void seed() {
        String tag = UUID.randomUUID().toString().substring(0, 8);

        transactions.executeWithoutResult(status -> {
            Category category = new Category();
            category.setName("Order page bench " + tag);
            category.setDescription("Benchmark fixture");
            categoryId = categoryRepository.save(category).getId();

            User customer = new User();
            customer.setUsername("pagebench-" + tag);
            customer.setEmail("pagebench-" + tag + "@example.test");
            customer.setPasswordHash("not-a-real-hash");
            customer.setFullName("Order Page Benchmark");
            customer.setRole(UserRole.CUSTOMER);
            customerId = userRepository.save(customer).getId();

            List<Product> products = new ArrayList<>();
            productIds = new ArrayList<>();

            for (int p = 0; p < PRODUCTS; p++) {
                Product product = new Product();
                product.setName("Bench product " + p + " " + tag);
                product.setDescription("Benchmark fixture");
                product.setPrice(BigDecimal.valueOf(10 + p).setScale(2));
                product.setSku(("PB-" + tag + "-" + p).toUpperCase());
                product.setCategory(category);
                Product saved = productRepository.save(product);

                Inventory inventory = new Inventory();
                inventory.setProduct(saved);
                inventory.setQuantity(100);
                inventoryRepository.save(inventory);

                products.add(saved);
                productIds.add(saved.getId());
            }

            for (int o = 0; o < ORDERS; o++) {
                Order order = new Order();
                order.setUser(customer);
                order.setOrderDate(LocalDateTime.now().minusHours(o));

                for (int line = 0; line < LINES_PER_ORDER; line++) {
                    // Distinct products per order, so the products cannot all be
                    // served from one already-loaded proxy.
                    Product product = products.get((o * LINES_PER_ORDER + line) % products.size());
                    OrderItem item = new OrderItem();
                    item.setProduct(product);
                    item.setQuantity(1 + line);
                    item.setUnitPrice(product.getPrice());
                    order.addItem(item);
                }

                order.setTotalAmount(order.calculateTotal());
                orderRepository.save(order);
            }
        });
    }

    private void cleanUp() {
        transactions.executeWithoutResult(status -> {
            orderRepository.deleteAll(orderRepository.findByUserIdOrderByOrderDateDesc(customerId));
            productIds.forEach(id -> {
                inventoryRepository.findByProductId(id).ifPresent(inventoryRepository::delete);
                productRepository.deleteById(id);
            });
            userRepository.deleteById(customerId);
            categoryRepository.deleteById(categoryId);
        });
    }
}
