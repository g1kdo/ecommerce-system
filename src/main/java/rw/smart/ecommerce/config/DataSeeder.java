package rw.smart.ecommerce.config;

import com.mongodb.MongoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
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
import rw.smart.ecommerce.core.product.service.SkuGenerator;
import rw.smart.ecommerce.core.review.dao.ReviewRepository;
import rw.smart.ecommerce.core.review.model.Review;
import rw.smart.ecommerce.core.user.dao.UserRepository;
import rw.smart.ecommerce.core.user.enums.UserRole;
import rw.smart.ecommerce.core.user.model.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Populates the database on startup so a freshly cloned checkout has something
 * to browse, order and review.
 *
 * Three rules keep this safe to leave switched on:
 *
 * 1. It never runs in {@code prod} — seeding a production database with fake
 *    users and orders is the kind of mistake that is hard to undo.
 * 2. Each block is guarded by an emptiness check, so restarting the application
 *    does not duplicate rows. Editing a seeded row and restarting keeps the edit.
 * 3. The MongoDB half is best-effort. An unreachable document store costs the
 *    sample reviews, not the relational seed that already committed.
 *
 * Disable explicitly with {@code app.seed.enabled=false}.
 */
@Slf4j
@Configuration
@Profile("!prod")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder {

    /**
     * The work itself lives in a bean of its own so {@code @Transactional} is
     * actually honoured. Constructing a {@code Seeder} inline inside the runner
     * would hand back a raw object with no proxy around it, and the whole seed
     * would run outside any transaction.
     */
    @Bean
    public Seeder seeder(@Value("${app.seed.admin-email:admin@smartecommerce.rw}") String adminEmail,
                         @Value("${app.seed.admin-password:Admin@12345}") String adminPassword,
                         UserRepository userRepository,
                         CategoryRepository categoryRepository,
                         ProductRepository productRepository,
                         InventoryRepository inventoryRepository,
                         OrderRepository orderRepository,
                         ReviewRepository reviewRepository,
                         PasswordEncoder passwordEncoder) {

        return new Seeder(adminEmail, adminPassword, userRepository, categoryRepository, productRepository,
                inventoryRepository, orderRepository, reviewRepository, passwordEncoder);
    }

    @Bean
    public ApplicationRunner seedData(Seeder seeder) {
        return args -> seeder.run();
    }

    @Slf4j
    public static class Seeder {

        private final UserRepository userRepository;
        private final CategoryRepository categoryRepository;
        private final ProductRepository productRepository;
        private final InventoryRepository inventoryRepository;
        private final OrderRepository orderRepository;
        private final ReviewRepository reviewRepository;
        private final PasswordEncoder passwordEncoder;
        private final String adminEmail;
        private final String adminPassword;

        public Seeder(String adminEmail,
                      String adminPassword,
                      UserRepository userRepository,
                      CategoryRepository categoryRepository,
                      ProductRepository productRepository,
                      InventoryRepository inventoryRepository,
                      OrderRepository orderRepository,
                      ReviewRepository reviewRepository,
                      PasswordEncoder passwordEncoder) {
            this.userRepository = userRepository;
            this.categoryRepository = categoryRepository;
            this.productRepository = productRepository;
            this.inventoryRepository = inventoryRepository;
            this.orderRepository = orderRepository;
            this.reviewRepository = reviewRepository;
            this.passwordEncoder = passwordEncoder;
            this.adminEmail = adminEmail;
            this.adminPassword = adminPassword;
        }

        @Transactional
        public void run() {
            log.info("Data seeder starting");

            List<User> users = seedUsers();
            ensureAdminAccount();
            List<Category> categories = seedCategories();
            List<Product> products = seedProducts(categories);
            seedOrders(users, products);
            seedReviews(users, products);

            log.info("Data seeder finished");
        }

        // ---------- Users ----------

        /**
         * Guarantees one administrator account with credentials the operator
         * actually knows.
         *
         * This runs outside {@link #seedUsers()} on purpose. That method skips
         * entirely when the table already has rows, which is right for sample
         * data but leaves an upgraded database with whatever accounts it happened
         * to inherit — possibly none that can sign in, and therefore no way to
         * reach any admin endpoint. A database migrated from Phase 1 hits exactly
         * that: its administrator row carries a placeholder that no password can
         * ever match.
         *
         * Existing accounts are never modified. If the address is already present,
         * this does nothing at all — so it cannot reset a password an operator has
         * deliberately changed.
         */
        private void ensureAdminAccount() {
            if (userRepository.existsByEmailIgnoreCase(adminEmail)) {
                log.info("Administrator account {} already exists - left untouched", adminEmail);
                return;
            }

            User admin = user("admin", adminEmail, adminPassword, "System Administrator",
                    "+250788000001", UserRole.ADMIN);

            // A username collision is possible even when the e-mail is free.
            if (userRepository.existsByUsernameIgnoreCase(admin.getUsername()))
                admin.setUsername("admin_" + System.nanoTime() % 100_000);

            userRepository.save(admin);

            log.warn("""

                    ==========================================================
                     Bootstrap administrator account created
                       email    : {}
                       password : {}
                     Change this before exposing the service to anyone else.
                     Disable with app.seed.enabled=false
                    ==========================================================""", adminEmail, adminPassword);
        }

        private List<User> seedUsers() {
            if (userRepository.count() > 0) {
                log.info("Users already present ({}) - skipping", userRepository.count());
                return userRepository.findAll();
            }

            List<User> users = List.of(
                    user("admin", "admin@smartecommerce.rw", "Admin@12345", "System Administrator",
                            "+250788000001", UserRole.ADMIN),
                    user("kmugisha", "k.mugisha@example.com", "Customer@123", "Kevine Mugisha",
                            "+250788000002", UserRole.CUSTOMER),
                    user("jdoe", "j.doe@example.com", "Customer@123", "John Doe",
                            "+250788000003", UserRole.CUSTOMER),
                    user("aingabire", "a.ingabire@example.com", "Customer@123", "Alice Ingabire",
                            "+250788000004", UserRole.CUSTOMER));

            List<User> saved = userRepository.saveAll(users);
            log.info("Seeded {} users (admin@smartecommerce.rw / Admin@12345)", saved.size());
            return saved;
        }

        private User user(String username, String email, String password, String fullName,
                          String phone, UserRole role) {

            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPasswordHash(hash(password));
            user.setFullName(fullName);
            user.setPhone(phone);
            user.setRole(role);
            return user;
        }

        // ---------- Categories ----------

        private List<Category> seedCategories() {
            if (categoryRepository.count() > 0) {
                log.info("Categories already present ({}) - skipping", categoryRepository.count());
                return categoryRepository.findAll();
            }

            List<Category> categories = List.of(
                    category("Electronics", "Devices, gadgets and accessories"),
                    category("Peripherals", "Mice, keyboards, headsets and webcams"),
                    category("Home & Kitchen", "Household and kitchen essentials"),
                    category("Books", "Printed and digital books"),
                    category("Audio", "Headphones, earbuds and speakers"));

            List<Category> saved = categoryRepository.saveAll(categories);
            log.info("Seeded {} categories", saved.size());
            return saved;
        }

        private Category category(String name, String description) {
            Category category = new Category();
            category.setName(name);
            category.setDescription(description);
            return category;
        }

        // ---------- Products and stock ----------

        private List<Product> seedProducts(List<Category> categories) {
            if (productRepository.count() > 0) {
                log.info("Products already present ({}) - skipping", productRepository.count());
                return productRepository.findAll();
            }

            Map<String, Category> byName = new java.util.HashMap<>();
            categories.forEach(category -> byName.put(category.getName(), category));

            // Deliberately varied prices and stock levels so filtering, sorting and
            // the out-of-stock path all have something real to exercise.
            List<Product> products = List.of(
                    product("Wireless Mouse", "Ergonomic 2.4GHz wireless mouse", "19.99", byName.get("Peripherals")),
                    product("Mechanical Keyboard", "RGB backlit, blue switches", "59.99", byName.get("Peripherals")),
                    product("USB-C Hub", "7-in-1 aluminium hub", "34.50", byName.get("Peripherals")),
                    product("27\" 4K Monitor", "IPS panel, 60Hz, HDR10", "329.00", byName.get("Electronics")),
                    product("Laptop Stand", "Adjustable aluminium stand", "42.75", byName.get("Electronics")),
                    product("Portable SSD 1TB", "USB 3.2 Gen 2, 1050MB/s", "119.90", byName.get("Electronics")),
                    product("Studio Headphones", "Over-ear, closed back", "149.99", byName.get("Audio")),
                    product("Bluetooth Speaker", "Waterproof, 12h battery", "64.00", byName.get("Audio")),
                    product("Electric Kettle", "1.7L stainless steel", "28.40", byName.get("Home & Kitchen")),
                    product("Chef's Knife", "20cm high-carbon steel", "45.00", byName.get("Home & Kitchen")),
                    product("Clean Code", "Robert C. Martin", "38.25", byName.get("Books")),
                    product("Designing Data-Intensive Applications", "Martin Kleppmann", "52.80", byName.get("Books")));

            List<Product> saved = productRepository.saveAll(products);

            // Same two-phase assignment the product service uses: the SKU embeds
            // the id, so it can only be built once the rows have been written.
            LocalDate today = LocalDate.now();
            saved.forEach(product ->
                    product.setSku(SkuGenerator.generate(product.getCategory(), product.getId(), today)));
            saved = productRepository.saveAll(saved);

            int[] quantities = {150, 80, 45, 12, 60, 25, 30, 55, 90, 40, 20, 0};
            List<Inventory> stock = new ArrayList<>();
            for (int i = 0; i < saved.size(); i++) {
                Inventory inventory = new Inventory();
                inventory.setProduct(saved.get(i));
                // The last product is seeded at zero on purpose, so the
                // insufficient-stock path is reachable without editing data first.
                inventory.setQuantity(quantities[i]);
                stock.add(inventory);
            }
            inventoryRepository.saveAll(stock);

            log.info("Seeded {} products with inventory", saved.size());
            return saved;
        }

        private Product product(String name, String description, String price, Category category) {
            Product product = new Product();
            product.setName(name);
            product.setDescription(description);
            product.setPrice(new BigDecimal(price));
            product.setCategory(category);
            // Placeholder only; replaced with the real SKU once the id exists.
            product.setSku("TMP-" + java.util.UUID.randomUUID());
            return product;
        }

        // ---------- Orders ----------

        private void seedOrders(List<User> users, List<Product> products) {
            if (orderRepository.count() > 0) {
                log.info("Orders already present ({}) - skipping", orderRepository.count());
                return;
            }
            if (users.isEmpty() || products.isEmpty()) return;

            User customer = users.stream()
                    .filter(user -> user.getRole() == UserRole.CUSTOMER)
                    .findFirst()
                    .orElse(users.get(0));

            // Same reasoning as seedReviews: an existing database may hold fewer
            // products than the seed defines, so positions are read defensively.
            Product first = at(products, 0);
            Product second = at(products, 1);
            Product seventh = at(products, 6);

            List<Order> orders = new ArrayList<>();
            if (first != null && second != null)
                orders.add(order(customer, OrderStatus.DELIVERED, item(first, 2), item(second, 1)));

            if (seventh != null)
                orders.add(order(customer, OrderStatus.PENDING, item(seventh, 1)));

            if (orders.isEmpty()) {
                log.info("Not enough products to seed orders - skipping");
                return;
            }

            orderRepository.saveAll(orders);

            // The seeded orders consume stock, exactly as a real checkout would —
            // otherwise the catalogue would advertise quantities the order history
            // has already spent.
            orders.forEach(this::decrementSeededStock);

            log.info("Seeded {} orders for {}", orders.size(), customer.getEmail());
        }

        private Order order(User user, OrderStatus status, OrderItem... items) {
            Order order = new Order();
            order.setUser(user);
            order.setStatus(status);
            for (OrderItem item : items) {
                order.addItem(item);
            }
            order.setTotalAmount(order.calculateTotal());
            return order;
        }

        private OrderItem item(Product product, int quantity) {
            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setUnitPrice(product.getPrice());
            return item;
        }

        private void decrementSeededStock(Order order) {
            for (OrderItem item : order.getItems()) {
                inventoryRepository.decrementQuantity(item.getProduct().getId(), item.getQuantity());
            }
        }

        // ---------- Reviews (document store) ----------

        /**
         * Positions are read through {@link #at} rather than {@code List.get}.
         * When an earlier block skipped, the list is whatever the database already
         * held — which may be shorter than the seed data — and a fixed index would
         * fail startup on an existing database.
         */
        private void seedReviews(List<User> users, List<Product> products) {
            if (users.isEmpty() || products.isEmpty()) return;

            try {
                if (reviewRepository.countByProductId(products.get(0).getId()) > 0) {
                    log.info("Reviews already present - skipping");
                    return;
                }

                List<Review> reviews = new ArrayList<>();
                addReview(reviews, at(products, 0), at(users, 1), 5, "Excellent value",
                        "Comfortable for long sessions and the battery lasts weeks.",
                        List.of("ergonomic", "value"));
                addReview(reviews, at(products, 0), at(users, 2), 4, "Solid mouse",
                        "Tracking is precise. Slightly small for large hands.",
                        List.of("precise"));
                addReview(reviews, at(products, 1), at(users, 1), 5, "Fantastic typing feel",
                        "Blue switches are loud but the build quality is superb.",
                        List.of("tactile", "durable"));
                addReview(reviews, at(products, 6), at(users, 3), 4, "Great for the price",
                        "Neutral sound signature, comfortable earcups.",
                        List.of("balanced"));

                reviews.forEach(reviewRepository::insert);
                log.info("Seeded {} reviews into MongoDB", reviews.size());

            } catch (MongoException e) {
                // Relational seeding has already committed; losing sample reviews is
                // not a reason to fail startup.
                log.warn("Could not seed reviews - document store unavailable: {}", e.getMessage());
            }
        }

        /** Skips the entry rather than failing when the referenced row is absent. */
        private void addReview(List<Review> target, Product product, User user, int rating,
                               String title, String comment, List<String> tags) {

            if (product == null || user == null) return;
            target.add(review(product, user, rating, title, comment, tags));
        }

        private static <T> T at(List<T> items, int index) {
            return index >= 0 && index < items.size() ? items.get(index) : null;
        }

        private Review review(Product product, User user, int rating, String title,
                              String comment, List<String> tags) {

            Review review = new Review();
            review.setProductId(product.getId());
            review.setUserId(user.getId());
            review.setRating(rating);
            review.setTitle(title);
            review.setComment(comment);
            review.setTags(new ArrayList<>(tags));
            review.setHelpfulVotes(0);
            review.setCreatedAt(Instant.now());
            review.setUpdatedAt(review.getCreatedAt());
            return review;
        }

        /** Same encoder the user service uses, so seeded accounts can sign in. */
        private String hash(String plainText) {
            return passwordEncoder.encode(plainText);
        }
    }
}
