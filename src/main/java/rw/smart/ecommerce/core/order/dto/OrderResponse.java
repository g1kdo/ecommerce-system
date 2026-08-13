package rw.smart.ecommerce.core.order.dto;

import rw.smart.ecommerce.core.order.model.Order;
import rw.smart.ecommerce.core.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponse(
        Long id,
        Long userId,
        OrderStatus status,
        BigDecimal totalAmount,
        String orderDate,
        List<OrderItemResponse> items) {

    /** Must be called inside the transaction — it walks the lazy item list. */
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getOrderDate() == null ? null : order.getOrderDate().toString(),
                order.getItems().stream().map(OrderItemResponse::from).toList());
    }
}
