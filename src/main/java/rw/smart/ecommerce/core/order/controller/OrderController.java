package rw.smart.ecommerce.core.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.smart.ecommerce.core.order.dto.OrderRequest;
import rw.smart.ecommerce.core.order.dto.OrderResponse;
import rw.smart.ecommerce.core.order.dto.OrderStatusRequest;
import rw.smart.ecommerce.core.order.enums.OrderStatus;
import rw.smart.ecommerce.core.order.service.OrderService;
import rw.smart.ecommerce.utils.response.PageResponse;
import rw.smart.ecommerce.utils.response.StandardResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Checkout and order history (customer), oversight and status transitions (admin)")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "Place an order",
            description = """
                    Prices the basket, reserves stock and writes the order in a single
                    transaction. Returns 409 if any line exceeds available stock, in which
                    case no stock is reserved and no order is created.""")
    @PostMapping
    public ResponseEntity<StandardResponse<OrderResponse>> placeOrder(@Valid @RequestBody OrderRequest request) {
        OrderResponse order = orderService.placeOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StandardResponse.created("Order placed successfully", order));
    }

    @Operation(summary = "Get a single order by id")
    @GetMapping("/{id}")
    public ResponseEntity<StandardResponse<OrderResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(StandardResponse.ok("Order retrieved successfully", orderService.findById(id)));
    }

    @Operation(summary = "Get the order history for a user")
    @GetMapping
    public ResponseEntity<StandardResponse<List<OrderResponse>>> findByUser(@RequestParam Long userId) {
        List<OrderResponse> orders = orderService.findByUser(userId);
        return ResponseEntity.ok(StandardResponse.ok(orders.size() + " order(s) retrieved", orders));
    }

    @Operation(summary = "Get a paginated order history for a user",
            description = """
                    The paginated form of the endpoint above, optionally narrowed to one
                    status. Prefer it: the unpaginated version returns a customer's entire
                    history in one response, which has no upper bound.""")
    @GetMapping("/history")
    public ResponseEntity<StandardResponse<PageResponse<OrderResponse>>> history(
            @RequestParam Long userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction) {

        PageResponse<OrderResponse> orders =
                orderService.findByUser(userId, status, page, size, sortBy, direction);

        return ResponseEntity.ok(StandardResponse.ok("Order history retrieved successfully", orders));
    }

    @Operation(summary = "Get all orders with pagination (Admin only)",
            description = "Optionally filtered by status. Sortable by id, orderDate, totalAmount or status.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<StandardResponse<PageResponse<OrderResponse>>> findAll(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction) {

        PageResponse<OrderResponse> orders = orderService.findAll(status, page, size, sortBy, direction);
        return ResponseEntity.ok(StandardResponse.ok("Orders retrieved successfully", orders));
    }

    @Operation(summary = "Move an order to the next status (Admin only)",
            description = """
                    Transitions are validated: PENDING to PAID or CANCELLED, PAID to SHIPPED
                    or CANCELLED, SHIPPED to DELIVERED. DELIVERED and CANCELLED are final.
                    Cancelling returns the reserved stock.""")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<StandardResponse<OrderResponse>> updateStatus(
            @PathVariable Long id, @Valid @RequestBody OrderStatusRequest request) {

        OrderResponse updated = orderService.updateStatus(id, request.status());
        return ResponseEntity.ok(StandardResponse.ok("Order status updated to " + request.status(), updated));
    }
}
