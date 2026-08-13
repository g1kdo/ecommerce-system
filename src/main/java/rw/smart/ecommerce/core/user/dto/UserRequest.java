package rw.smart.ecommerce.core.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import rw.smart.ecommerce.core.user.enums.UserRole;

public record UserRequest(

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format (e.g. example@gmail.com)")
        @Size(max = 120, message = "Email must not exceed 120 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
        String password,

        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must not exceed 120 characters")
        String fullName,

        @Pattern(regexp = "^$|^\\+?[0-9 .-]{7,20}$", message = "Invalid phone number")
        String phone,

        @NotNull(message = "Role is required")
        UserRole role) {
}
