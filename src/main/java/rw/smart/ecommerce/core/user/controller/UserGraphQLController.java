package rw.smart.ecommerce.core.user.controller;

import jakarta.validation.Valid;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import rw.smart.ecommerce.core.user.dto.UserRequest;
import rw.smart.ecommerce.core.user.dto.UserResponse;
import rw.smart.ecommerce.core.user.service.UserService;

import java.util.List;

/** GraphQL entry points for user administration. */
@Controller
public class UserGraphQLController {

    private final UserService userService;

    public UserGraphQLController(UserService userService) {
        this.userService = userService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @QueryMapping
    public List<UserResponse> users() {
        return userService.findAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @QueryMapping
    public UserResponse user(@Argument Long id) {
        return userService.findById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public UserResponse createUser(@Argument @Valid UserRequest input) {
        return userService.create(input);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public boolean deleteUser(@Argument Long id) {
        userService.delete(id);
        return true;
    }
}
