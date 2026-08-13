package rw.smart.ecommerce.core.user.dto;

import rw.smart.ecommerce.core.user.model.User;
import rw.smart.ecommerce.core.user.enums.UserRole;

/**
 * Never carries {@code passwordHash} — the entity is not exposed directly
 * precisely so a credential cannot leak through a serialized response.
 */
public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String phone,
        UserRole role,
        String createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
    }
}
