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

    @QueryMapping
    public List<OrderResponse> ordersByUser(@Argument Long userId) {
        return orderService.findByUser(userId);
    }

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
