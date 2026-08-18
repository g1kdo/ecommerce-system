package rw.smart.ecommerce.core.order.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rw.smart.ecommerce.config.CacheConfig;
import rw.smart.ecommerce.core.audit.service.CheckoutAuditService;
import rw.smart.ecommerce.core.inventory.model.Inventory;
import rw.smart.ecommerce.utils.pagination.PaginationSupport;
import rw.smart.ecommerce.core.order.model.Order;
import rw.smart.ecommerce.core.order.model.item.OrderItem;
import rw.smart.ecommerce.core.product.model.Product;
import rw.smart.ecommerce.core.user.model.User;
import rw.smart.ecommerce.core.order.enums.OrderStatus;
import rw.smart.ecommerce.utils.response.PageResponse;
import rw.smart.ecommerce.core.order.dto.OrderItemRequest;
import rw.smart.ecommerce.core.order.dto.OrderRequest;
import rw.smart.ecommerce.core.order.dto.OrderResponse;
import rw.smart.ecommerce.core.inventory.dao.InventoryRepository;
import rw.smart.ecommerce.core.order.dao.OrderRepository;
import rw.smart.ecommerce.core.product.dao.ProductRepository;
import rw.smart.ecommerce.core.user.dao.UserRepository;
import rw.smart.ecommerce.core.order.service.OrderService;
import rw.smart.ecommerce.utils.exceptions.InsufficientStockException;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;
import rw.smart.ecommerce.utils.exceptions.ResourceNotFoundException;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    /**
     * Legal status transitions. Without this a shipped order could be moved back
     * to PENDING by a mistyped request, and the history would silently accept it.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.PAID, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));

    /**
     * A checkout that has not finished within this many seconds is holding
     * inventory row locks that every other customer buying the same product is
     * queued behind. Failing it is better than letting that queue grow.
     */
    private static final int CHECKOUT_TIMEOUT_SECONDS = 15;

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final CheckoutAuditService checkoutAudit;
    private final PaginationSupport pagination;

    public OrderServiceImpl(OrderRepository orderRepository,
                            UserRepository userRepository,
                            ProductRepository productRepository,
                            InventoryRepository inventoryRepository,
                            CheckoutAuditService checkoutAudit,
                            PaginationSupport pagination) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.checkoutAudit = checkoutAudit;
        this.pagination = pagination;
    }

    /**
     * The one place in the system that genuinely needs ACID guarantees: pricing
     * the basket, writing the order with its lines and reserving stock must
     * either all happen or none of them. A rollback here leaves no half-written
     * order and no stock reserved against an order that was never created.
     *
     * <h4>Why these transaction attributes</h4>
     *
     * {@code REQUIRED} — the default, stated explicitly because it is load
     * bearing. A caller that already has a transaction must not get a second one
     * here; the reservation and the order row belong to the same unit of work.
     *
     * {@code READ_COMMITTED} — also PostgreSQL's default, and again stated so the
     * assumption is visible rather than inherited from a server setting somebody
     * could change. Nothing here needs more. The conditional decrement below
     * makes "check the stock" and "take the stock" a single atomic statement, so
     * there is no read-then-write window for a stronger level to protect. Going
     * up to {@code REPEATABLE_READ} would not make this safer — it would make two
     * customers buying the same product abort each other with serialization
     * failures instead of queueing.
     *
     * {@code rollbackFor = Exception.class} — Spring rolls back on unchecked
     * exceptions only. Every failure path here happens to be unchecked today, so
     * this changes nothing now; it is here so that adding a checked exception to
     * this method later cannot silently commit a half-finished checkout.
     */
    @Override
    // An order moves stock on several products at once, and the cached product
    // detail carries a stock figure - so the whole cache goes. This runs only if
    // the method returns normally: @CacheEvict defaults to beforeInvocation =
    // false, which is what leaves the cache untouched by a rolled-back checkout.
    @CacheEvict(value = CacheConfig.PRODUCTS, allEntries = true)
    @Transactional(propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED,
            timeout = CHECKOUT_TIMEOUT_SECONDS,
            rollbackFor = Exception.class)
    public OrderResponse placeOrder(OrderRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", request.userId()));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);

        for (Map.Entry<Long, Integer> line : consolidate(request.items()).entrySet()) {
            Long productId = line.getKey();
            int quantity = line.getValue();

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Product", productId));

            // Conditional decrement: 0 rows updated means the stock was not there.
            int reserved = inventoryRepository.decrementQuantity(productId, quantity);
            if (reserved == 0) {
                auditShortfall(user.getId(), productId, quantity);

                // Unchecked, so it rolls the transaction back on its way out —
                // including every reservation already taken for earlier lines.
                throw new InsufficientStockException("Insufficient stock for product "
                        + product.getName() + " (requested " + quantity + ").");
            }

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(quantity);
            // Snapshot, not a reference: a later price change must not rewrite
            // the value of an order that has already been placed.
            item.setUnitPrice(product.getPrice());
            order.addItem(item);
        }

        order.setTotalAmount(order.calculateTotal());
        Order saved = orderRepository.save(order);

        log.info("Order {} placed by user {} for {}", saved.getId(), user.getId(), saved.getTotalAmount());
        return OrderResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        return orderRepository.findWithItemsById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> findByUser(Long userId) {
        if (!userRepository.existsById(userId))
            throw ResourceNotFoundException.of("User", userId);

        return orderRepository.findByUserIdOrderByOrderDateDesc(userId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    /**
     * The paginated history. The page of orders is fetched without a fetch join —
     * a join fetch under LIMIT/OFFSET pages the wrong rows — and the lines and
     * their products are then resolved for the whole page by Hibernate's batch
     * fetching, configured as {@code default_batch_fetch_size} in
     * {@code application.properties}.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> findByUser(Long userId, OrderStatus status, Integer page, Integer size,
                                                  String sortBy, String direction) {

        if (!userRepository.existsById(userId))
            throw ResourceNotFoundException.of("User", userId);

        Pageable pageable = pagination.forOrders(page, size, sortBy, direction);

        Page<Order> orders = status == null
                ? orderRepository.findByUserId(userId, pageable)
                : orderRepository.findByUserIdAndStatus(userId, status, pageable);

        return PageResponse.from(orders, OrderResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> findAll(OrderStatus status, Integer page, Integer size,
                                               String sortBy, String direction) {

        Pageable pageable = pagination.forOrders(page, size, sortBy, direction);

        Page<Order> orders = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);

        return PageResponse.from(orders, OrderResponse::from);
    }

    /**
     * Cancelling returns stock, so this write is transactional for the same
     * reason {@code placeOrder} is: the status change and the restock are one
     * fact, and a failure between them would leave an order marked CANCELLED
     * with its stock still reserved against it.
     */
    @Override
    // Cancelling returns stock to the shelf, so the same reasoning applies.
    @CacheEvict(value = CacheConfig.PRODUCTS, allEntries = true)
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public OrderResponse updateStatus(Long id, OrderStatus status) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", id));

        OrderStatus current = order.getStatus();
        if (current == status) return OrderResponse.from(order);

        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(status))
            throw new InvalidInputException("Cannot move an order from " + current + " to " + status
                    + ". Allowed next states: " + (allowed.isEmpty() ? "none (final state)" : allowed));

        // Cancelling releases the stock this order was holding. Read the response
        // before the bulk update clears the persistence context.
        List<OrderItem> items = List.copyOf(order.getItems());
        order.setStatus(status);
        OrderResponse response = OrderResponse.from(orderRepository.save(order));

        if (status == OrderStatus.CANCELLED) {
            for (OrderItem item : items) {
                inventoryRepository.incrementQuantity(item.getProduct().getId(), item.getQuantity());
            }
            log.info("Order {} cancelled; stock returned for {} line(s)", id, items.size());
        }

        return response;
    }

    /**
     * Merges repeated product lines into one.
     *
     * A basket listing the same product twice is a client that added it twice,
     * not a request for two order lines. Left unmerged it also breaks the
     * reservation: two separate decrements of 3 against 5 units in stock take 3,
     * then fail on the second — rolling back a checkout that would have been
     * served as a single line of 6 had the stock been there, and reporting the
     * wrong requested quantity while doing it.
     *
     * A {@link LinkedHashMap} so the order of the lines is the order the customer
     * built the basket in.
     */
    private Map<Long, Integer> consolidate(List<OrderItemRequest> items) {
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderItemRequest line : items) {
            merged.merge(line.productId(), line.quantity(), Integer::sum);
        }
        return merged;
    }

    /**
     * Records the shortfall on a transaction of its own so the row survives the
     * rollback that is about to happen.
     *
     * The failure is caught here rather than inside the audit service: by the
     * time a catch block inside a {@code REQUIRES_NEW} method runs, that
     * transaction is already marked rollback-only and its commit would throw
     * anyway. This is also the only layer that knows the audit is optional and
     * the stock error is not — a customer waiting on a checkout must be told the
     * product is out of stock, whatever the audit table did.
     */
    private void auditShortfall(Long userId, Long productId, int requested) {
        try {
            int available = inventoryRepository.findByProductId(productId)
                    .map(Inventory::getQuantity)
                    .orElse(0);

            checkoutAudit.recordShortfall(userId, productId, requested, available);

        } catch (RuntimeException e) {
            log.warn("Checkout shortfall for product {} could not be audited: {}", productId, e.getMessage());
        }
    }
}
