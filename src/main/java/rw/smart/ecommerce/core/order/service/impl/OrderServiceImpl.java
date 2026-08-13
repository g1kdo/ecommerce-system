package rw.smart.ecommerce.core.order.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.smart.ecommerce.config.CacheConfig;
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

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final PaginationSupport pagination;

    public OrderServiceImpl(OrderRepository orderRepository,
                            UserRepository userRepository,
                            ProductRepository productRepository,
                            InventoryRepository inventoryRepository,
                            PaginationSupport pagination) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.pagination = pagination;
    }

    /**
     * The one place in the system that genuinely needs ACID guarantees: pricing
     * the basket, writing the order with its lines and reserving stock must
     * either all happen or none of them. A rollback here leaves no half-written
     * order and no stock reserved against an order that was never created.
     */
    @Override
    // An order moves stock on several products at once, and the cached
    // product detail carries a stock figure - so the whole cache goes.
    @CacheEvict(value = CacheConfig.PRODUCTS, allEntries = true)
    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", request.userId()));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);

        for (OrderItemRequest line : request.items()) {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Product", line.productId()));

            // Conditional decrement: 0 rows updated means the stock was not there.
            int reserved = inventoryRepository.decrementQuantity(line.productId(), line.quantity());
            if (reserved == 0)
                throw new InsufficientStockException("Insufficient stock for product '"
                        + product.getName() + "' (requested " + line.quantity() + ").");

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(line.quantity());
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

    @Override
    // Cancelling returns stock to the shelf, so the same reasoning applies.
    @CacheEvict(value = CacheConfig.PRODUCTS, allEntries = true)
    @Transactional
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
}
