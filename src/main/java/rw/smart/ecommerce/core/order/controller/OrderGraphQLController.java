package rw.smart.ecommerce.core.order.controller;

import jakarta.validation.Valid;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import rw.smart.ecommerce.core.order.dto.OrderRequest;
import rw.smart.ecommerce.core.order.dto.OrderResponse;
import rw.smart.ecommerce.core.order.enums.OrderStatus;
import rw.smart.ecommerce.core.order.service.OrderService;

import java.util.List;

/**
 * GraphQL entry points for orders — the operation that benefits most from one
 * round trip: a client fetches an order with its items and each item's product
 * in a single request.
 */
@Controller
public class OrderGraphQLController {

    private final OrderService orderService;

    public OrderGraphQLController(OrderService orderService) {
        this.orderService = orderService;
    }

    /*
     * These two queries carry their own authorisation and must keep it.
     *
     * The URL rules in SecurityConfig protect /api/v1/orders/**, but GraphQL is a
     * single POST to /graphql that has to stay open for the public catalogue
     * queries — so no path rule can distinguish "list categories" from "read a
     * stranger's order history". Authorisation for GraphQL lives here or nowhere,
     * and leaving it off made order history world-readable while the equivalent
     * REST endpoint correctly answered 401.
     */

    @PreAuthorize("isAuthenticated()")
    @QueryMapping
    public List<OrderResponse> ordersByUser(@Argument Long userId) {
        return orderService.findByUser(userId);
    }

    @PreAuthorize("isAuthenticated()")
    @QueryMapping
    public OrderResponse order(@Argument Long id) {
        return orderService.findById(id);
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    public OrderResponse placeOrder(@Argument @Valid OrderRequest input) {
        return orderService.placeOrder(input);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public OrderResponse updateOrderStatus(@Argument Long id, @Argument OrderStatus status) {
        return orderService.updateStatus(id, status);
    }
}
