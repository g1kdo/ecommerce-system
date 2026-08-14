package rw.smart.ecommerce.core.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReviewRequest(

        @NotNull(message = "Product is required")
        Long productId,

        @NotNull(message = "User is required")
        Long userId,

        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5")
        Integer rating,

        @Size(max = 150, message = "Title must not exceed 150 characters")
        String title,

        @Size(max = 4000, message = "Comment must not exceed 4000 characters")
        String comment,

        List<String> tags) {
}
