package rw.smart.ecommerce.core.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.smart.ecommerce.core.user.dto.UserRequest;
import rw.smart.ecommerce.core.user.dto.UserResponse;
import rw.smart.ecommerce.core.user.service.UserService;
import rw.smart.ecommerce.utils.response.PageResponse;
import rw.smart.ecommerce.utils.response.StandardResponse;

import java.util.List;

/**
 * Account management. Every operation here is administrative, so the role check
 * is declared once on the class rather than repeated on each method — repeating
 * it invites the omission that makes one endpoint quietly public.
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users", description = "User account management (admin only)")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get all users (Admin only)")
    @GetMapping
    public ResponseEntity<StandardResponse<List<UserResponse>>> findAll() {
        List<UserResponse> users = userService.findAll();
        return ResponseEntity.ok(StandardResponse.ok(users.size() + " user(s) retrieved", users));
    }

    @Operation(summary = "Get all users with pagination (Admin only)",
            description = "`keyword` matches full name or e-mail. Sortable by id, username, email, fullName or createdAt.")
    @GetMapping("/search")
    public ResponseEntity<StandardResponse<PageResponse<UserResponse>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction) {

        PageResponse<UserResponse> results = userService.search(keyword, page, size, sortBy, direction);
        return ResponseEntity.ok(StandardResponse.ok("Users retrieved successfully", results));
    }

    @Operation(summary = "Get a single user by id (Admin only)")
    @GetMapping("/{id}")
    public ResponseEntity<StandardResponse<UserResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(StandardResponse.ok("User retrieved successfully", userService.findById(id)));
    }

    @Operation(summary = "Create a user account (Admin only)",
            description = "The password is stored BCrypt-hashed and is never returned by any endpoint.")
    @PostMapping
    public ResponseEntity<StandardResponse<UserResponse>> create(@Valid @RequestBody UserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StandardResponse.created("User created successfully", created));
    }

    @Operation(summary = "Update a user account (Admin only)")
    @PutMapping("/{id}")
    public ResponseEntity<StandardResponse<UserResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UserRequest request) {

        return ResponseEntity.ok(StandardResponse.ok("User updated successfully", userService.update(id, request)));
    }

    @Operation(summary = "Delete a user account (Admin only)",
            description = "Rejected with 409 when the user has existing orders.")
    @DeleteMapping("/{id}")
    public ResponseEntity<StandardResponse<Void>> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.ok(StandardResponse.ok("User deleted successfully", null));
    }
}
