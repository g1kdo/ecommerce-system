package rw.smart.ecommerce.core.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StockRequest(

        @NotNull(message = "Quantity is required")
        @PositiveOrZero(message = "Stock quantity cannot be negative")
        Integer quantity) {
}
