package rw.smart.ecommerce.core.user.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.smart.ecommerce.utils.pagination.PaginationSupport;
import rw.smart.ecommerce.core.user.model.User;
import rw.smart.ecommerce.utils.response.PageResponse;
import rw.smart.ecommerce.core.user.dto.UserRequest;
import rw.smart.ecommerce.core.user.dto.UserResponse;
import rw.smart.ecommerce.core.order.dao.OrderRepository;
import rw.smart.ecommerce.core.user.dao.UserRepository;
import rw.smart.ecommerce.core.user.service.UserService;
import rw.smart.ecommerce.utils.exceptions.DuplicateResourceException;
import rw.smart.ecommerce.utils.exceptions.ResourceNotFoundException;

import java.util.List;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaginationSupport pagination;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                           OrderRepository orderRepository,
                           PaginationSupport pagination,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.pagination = pagination;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UserResponse create(UserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email()))
            throw new DuplicateResourceException("Email already registered: " + request.email());

        if (userRepository.existsByUsernameIgnoreCase(request.username()))
            throw new DuplicateResourceException("Username already taken: " + request.username());

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(hash(request.password()));
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        user.setRole(request.role());

        User saved = userRepository.save(user);
        log.debug("Created user {} ({})", saved.getId(), saved.getEmail());
        return UserResponse.from(saved);
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));

        // Uniqueness is only re-checked when the value actually changed, so
        // saving an unmodified profile does not collide with the user's own row.
        if (!user.getEmail().equalsIgnoreCase(request.email())
                && userRepository.existsByEmailIgnoreCase(request.email()))
            throw new DuplicateResourceException("Email already registered: " + request.email());

        if (!user.getUsername().equalsIgnoreCase(request.username())
                && userRepository.existsByUsernameIgnoreCase(request.username()))
            throw new DuplicateResourceException("Username already taken: " + request.username());

        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        user.setRole(request.role());
        user.setPasswordHash(hash(request.password()));

        return UserResponse.from(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String keyword, Integer page, Integer size,
                                             String sortBy, String direction) {

        Pageable pageable = pagination.forUsers(page, size, sortBy, direction);

        Page<User> results = keyword == null || keyword.isBlank()
                ? userRepository.findAll(pageable)
                : userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        keyword.trim(), keyword.trim(), pageable);

        return PageResponse.from(results, UserResponse::from);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));

        // Orders reference users with ON DELETE RESTRICT; reporting the conflict
        // here gives a readable 409 instead of a constraint violation from the driver.
        if (!orderRepository.findByUserIdOrderByOrderDateDesc(id).isEmpty())
            throw new DuplicateResourceException(
                    "Cannot delete user " + id + " because they have existing orders.");

        userRepository.delete(user);
    }

    /**
     * BCrypt via the configured {@code PasswordEncoder}.
     *
     * The Phase 1 SHA-256 hash is gone: it was unsalted, so identical passwords
     * produced identical hashes, and fast, which is the opposite of what a
     * password hash should be. BCrypt is salted per row and deliberately slow.
     * The encoder is injected rather than instantiated so the algorithm is a
     * configuration decision, not one baked into this class.
     */
    private String hash(String plainText) {
        return passwordEncoder.encode(plainText);
    }
}
