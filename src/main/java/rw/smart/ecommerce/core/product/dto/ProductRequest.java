package rw.smart.ecommerce.core.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(

        @NotBlank(message = "Product name is required")
        @Size(max = 150, message = "Product name must not exceed 150 characters")
        String name,

        @Size(max = 4000, message = "Description must not exceed 4000 characters")
        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Price must have at most 2 decimal places")
        BigDecimal price,

        @NotBlank(message = "SKU is required")
        @Size(max = 40, message = "SKU must not exceed 40 characters")
        String sku,

        @NotNull(message = "Category is required")
        Long categoryId,

        /** Creates the matching inventory row; ignored on update. */
        @PositiveOrZero(message = "Initial stock cannot be negative")
        Integer initialStock) {
}
