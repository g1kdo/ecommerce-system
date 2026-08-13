package rw.smart.ecommerce.core.user.service;

import rw.smart.ecommerce.utils.response.PageResponse;
import rw.smart.ecommerce.core.user.dto.UserRequest;
import rw.smart.ecommerce.core.user.dto.UserResponse;

import java.util.List;

/** Administrator-facing user management. */
public interface UserService {

    UserResponse create(UserRequest request);

    UserResponse update(Long id, UserRequest request);

    UserResponse findById(Long id);

    List<UserResponse> findAll();

    PageResponse<UserResponse> search(String keyword, Integer page, Integer size, String sortBy, String direction);

    void delete(Long id);
}
