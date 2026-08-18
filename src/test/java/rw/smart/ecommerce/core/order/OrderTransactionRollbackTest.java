package rw.smart.ecommerce.core.order;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import rw.smart.ecommerce.core.audit.dao.CheckoutShortfallRepository;
import rw.smart.ecommerce.core.audit.model.CheckoutShortfall;
import rw.smart.ecommerce.core.category.dao.CategoryRepository;
import rw.smart.ecommerce.core.category.model.Category;
import rw.smart.ecommerce.core.inventory.dao.InventoryRepository;
import rw.smart.ecommerce.core.inventory.model.Inventory;
import rw.smart.ecommerce.core.order.dao.OrderRepository;
import rw.smart.ecommerce.core.order.dto.OrderItemRequest;
import rw.smart.ecommerce.core.order.dto.OrderRequest;
import rw.smart.ecommerce.core.order.dto.OrderResponse;
import rw.smart.ecommerce.core.order.enums.OrderStatus;
import rw.smart.ecommerce.core.order.service.OrderService;
import rw.smart.ecommerce.core.product.dao.ProductRepository;
import rw.smart.ecommerce.core.product.model.Product;
import rw.smart.ecommerce.core.user.dao.UserRepository;
import rw.smart.ecommerce.core.user.enums.UserRole;
import rw.smart.ecommerce.core.user.model.User;
import rw.smart.ecommerce.utils.exceptions.InsufficientStockException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the checkout transaction rolls back as a unit.
 *
 * <h4>Why this test is not annotated {@code @Transactional}</h4>
 *
 * The obvious way to write a JPA test is to put {@code @Transactional} on the
 * class and let Spring roll everything back afterwards. That would make this
 * test meaningless. The service would join the test's transaction instead of
 * opening its own, its rollback would become a rollback of the test, and the
 * assertions afterwards would be reading a persistence context that never
 * committed anything in the first place — the test would pass whether or not
 * {@code placeOrder} were transactional at all.
 *
 * So fixtures are written and committed for real, and {@link #tearDown()}
 * removes them. In exchange the assertions mean what they say: the rows either
 * are in the database or they are not.
 *
 * <h4>Requires PostgreSQL</h4>
 *
 * There is no embedded database on the classpath, so the {@code test} profile
 * points at a real one. Create it once:
 *
 * <pre>createdb -U postgres smart_ecommerce_test_db</pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Checkout transaction boundaries")
class OrderTransactionRollbackTest {

    private static final int PLENTIFUL = 10;
    private static final int SCARCE = 1;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private CheckoutShortfallRepository shortfallRepository;

    private User customer;
    private Category category;
    private Product stocked;
    private Product scarce;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);

        category = new Category();
        category.setName("Rollback Fixtures " + tag);
        category.setDescription("Created by OrderTransactionRollbackTest");
        category = categoryRepository.save(category);

        customer = new User();
        customer.setUsername("rollback-" + tag);
        customer.setEmail("rollback-" + tag + "@example.test");
        customer.setPasswordHash("not-a-real-hash");
        customer.setFullName("Rollback Fixture");
        customer.setRole(UserRole.CUSTOMER);
        customer = userRepository.save(customer);

        stocked = saveProduct("Well Stocked " + tag, "9.99", PLENTIFUL);
        scarce = saveProduct("Nearly Gone " + tag, "19.99", SCARCE);
    }

    @AfterEach
    void tearDown() {
        // Reverse dependency order: order items cascade from orders, inventory
        // must go before its product, and the audit rows have no foreign keys at
        // all so they can be removed whenever.
        orderRepository.deleteAll(orderRepository.findByUserIdOrderByOrderDateDesc(customer.getId()));
        shortfallRepository.deleteAll(shortfallRepository.findByUserIdOrderByRecordedAtDesc(customer.getId()));

        List.of(stocked, scarce).forEach(product -> {
            // Not deleteByProductId: a derived delete declared on the repository
            // interface carries no transaction of its own, and this teardown runs
            // outside one. CrudRepository.delete is transactional.
            inventoryRepository.findByProductId(product.getId()).ifPresent(inventoryRepository::delete);
            productRepository.deleteById(product.getId());
        });

        userRepository.deleteById(customer.getId());
        categoryRepository.deleteById(category.getId());
    }

    @Test
    @DisplayName("commits the order and the reservation together when every line has stock")
    void commitsWhenStockIsSufficient() {
        OrderResponse placed = orderService.placeOrder(new OrderRequest(
                customer.getId(), List.of(new OrderItemRequest(stocked.getId(), 3))));

        assertEquals(OrderStatus.PENDING, placed.status());
        assertEquals(0, new BigDecimal("29.97").compareTo(placed.totalAmount()),
                "three units at 9.99 should total 29.97");

        assertEquals(PLENTIFUL - 3, stockOf(stocked), "the reservation should have committed");
        assertEquals(1, orderRepository.findByUserIdOrderByOrderDateDesc(customer.getId()).size());
    }

    @Test
    @DisplayName("rolls back the reservations already taken when a later line is short")
    void rollsBackEarlierReservationsWhenALaterLineIsShort() {
        // Line one succeeds and decrements. Line two cannot be served. The point
        // of the test is what happens to line one.
        OrderRequest request = new OrderRequest(customer.getId(), List.of(
                new OrderItemRequest(stocked.getId(), 2),
                new OrderItemRequest(scarce.getId(), SCARCE + 5)));

        assertThrows(InsufficientStockException.class, () -> orderService.placeOrder(request));

        assertEquals(PLENTIFUL, stockOf(stocked),
                "the first line's reservation must have been rolled back, not left taken");
        assertEquals(SCARCE, stockOf(scarce),
                "the conditional decrement should not have moved stock it could not satisfy");

        assertTrue(orderRepository.findByUserIdOrderByOrderDateDesc(customer.getId()).isEmpty(),
                "no order row should survive a failed checkout");
    }

    @Test
    @DisplayName("records the shortfall on its own transaction, so it survives the rollback")
    void auditsTheShortfallDespiteTheRollback() {
        OrderRequest request = new OrderRequest(customer.getId(),
                List.of(new OrderItemRequest(scarce.getId(), SCARCE + 4)));

        assertThrows(InsufficientStockException.class, () -> orderService.placeOrder(request));

        List<CheckoutShortfall> audited = shortfallRepository.findByUserIdOrderByRecordedAtDesc(customer.getId());

        // This is the REQUIRES_NEW assertion. Under the default REQUIRED
        // propagation the insert would join the checkout transaction and be
        // rolled back with it, and this list would be empty.
        assertEquals(1, audited.size(), "the shortfall should have committed independently");
        assertEquals(scarce.getId(), audited.getFirst().getProductId());
        assertEquals(SCARCE + 4, audited.getFirst().getRequestedQuantity());
        assertEquals(SCARCE, audited.getFirst().getAvailableQuantity());

        assertTrue(orderRepository.findByUserIdOrderByOrderDateDesc(customer.getId()).isEmpty(),
                "the order itself must still have rolled back");
    }

    @Test
    @DisplayName("merges a repeated product into one line and reserves the total once")
    void mergesRepeatedLines() {
        OrderResponse placed = orderService.placeOrder(new OrderRequest(customer.getId(), List.of(
                new OrderItemRequest(stocked.getId(), 2),
                new OrderItemRequest(stocked.getId(), 3))));

        assertEquals(1, placed.items().size(), "the same product twice is one line, not two");
        assertEquals(5, placed.items().getFirst().quantity());
        assertEquals(PLENTIFUL - 5, stockOf(stocked));
    }

    @Test
    @DisplayName("returns the reserved stock when the order is cancelled")
    void cancellingReturnsStock() {
        OrderResponse placed = orderService.placeOrder(new OrderRequest(
                customer.getId(), List.of(new OrderItemRequest(stocked.getId(), 4))));

        assertEquals(PLENTIFUL - 4, stockOf(stocked));

        OrderResponse cancelled = orderService.updateStatus(placed.id(), OrderStatus.CANCELLED);

        assertEquals(OrderStatus.CANCELLED, cancelled.status());
        assertEquals(PLENTIFUL, stockOf(stocked), "cancelling should put the stock back on the shelf");
    }

    @Test
    @DisplayName("leaves no order behind when the basket names a product that does not exist")
    void rollsBackWhenAProductIsMissing() {
        OrderRequest request = new OrderRequest(customer.getId(), List.of(
                new OrderItemRequest(stocked.getId(), 1),
                new OrderItemRequest(Long.MAX_VALUE, 1)));

        assertThrows(RuntimeException.class, () -> orderService.placeOrder(request));

        assertEquals(PLENTIFUL, stockOf(stocked), "a missing product must not cost the first line its stock");
        assertFalse(orderRepository.existsByUserId(customer.getId()));
    }

    private Product saveProduct(String name, String price, int quantity) {
        Product product = new Product();
        product.setName(name);
        product.setDescription("Created by OrderTransactionRollbackTest");
        product.setPrice(new BigDecimal(price));
        // products.sku is varchar(40); a full UUID plus the prefix is 41.
        product.setSku("TEST-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        product.setCategory(category);
        Product saved = productRepository.save(product);

        Inventory inventory = new Inventory();
        inventory.setProduct(saved);
        inventory.setQuantity(quantity);
        inventoryRepository.save(inventory);

        return saved;
    }

    private int stockOf(Product product) {
        return inventoryRepository.findByProductId(product.getId())
                .map(Inventory::getQuantity)
                .orElseThrow(() -> new IllegalStateException("fixture lost its inventory row"));
    }
}
