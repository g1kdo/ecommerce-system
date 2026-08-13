package rw.smart.ecommerce.core.order.dto;

import jakarta.validation.constraints.NotNull;
import rw.smart.ecommerce.core.order.enums.OrderStatus;

public record OrderStatusRequest(

        @NotNull(message = "Status is required")
        OrderStatus status) {
}
