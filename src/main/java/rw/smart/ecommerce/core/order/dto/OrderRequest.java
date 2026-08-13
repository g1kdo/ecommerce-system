package rw.smart.ecommerce.core.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderRequest(

        @NotNull(message = "User is required")
        Long userId,

        /** {@code @Valid} cascades validation into every line of the basket. */
        @NotEmpty(message = "An order must contain at least one item")
        @Valid
        List<OrderItemRequest> items) {
}
