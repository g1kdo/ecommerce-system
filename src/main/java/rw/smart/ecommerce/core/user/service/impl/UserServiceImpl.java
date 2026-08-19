package rw.smart.ecommerce.core.user.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.smart.ecommerce.config.CacheConfig;
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
    // The updated profile is exactly what findById would return next, so the
    // entry is replaced rather than dropped. A rollback cannot leave a rejected
    // value cached: the cache manager defers the put until commit.
    @CachePut(value = CacheConfig.PROFILES, key = "#id")
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
    // A profile is read on every administrative screen that shows a name
    // against an order, and it changes when the account holder edits it -
    // which is rare and always goes through update() above.
    @Cacheable(value = CacheConfig.PROFILES, key = "#id")
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

    /**
     * Administrator user search, built as a Query by Example probe.
     *
     * <h4>Why Query by Example here</h4>
     *
     * The predicate is three optional string containments OR'd together, over a
     * single entity, with no ranges and no joins. That is the exact shape Query
     * by Example expresses well: fill in a probe, let null fields mean "don't
     * care", and let a matcher say how the non-null ones are compared.
     *
     * The derived method it replaces was
     * {@code findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase}, which
     * took the same value twice and did not search usernames — so an
     * administrator looking up "kmugisha" got nothing back, while the same person
     * was findable by their full name. Adding username to a derived method means
     * a 130-character name; here it is one more line on the probe.
     *
     * <h4>Where Query by Example was rejected</h4>
     *
     * It is not used anywhere else in this codebase, and the reason is the same
     * every time: a probe can only express equality and string matching against
     * one entity's own columns.
     *
     * <ul>
     *   <li><b>Products</b> — the catalogue filters on a price <em>range</em>. A
     *       probe has one slot per field, so it cannot say "between". These stay
     *       on {@code ProductSpecifications}.</li>
     *   <li><b>Orders</b> — same problem with the order-date window.</li>
     *   <li><b>Categories</b> — one searchable column. A probe, a matcher and an
     *       {@code Example.of} to replace {@code findByNameContainingIgnoreCase}
     *       would be more code saying less.</li>
     *   <li><b>Reports</b> — aggregates. Query by Example returns entities.</li>
     * </ul>
     *
     * <h4>What this costs</h4>
     *
     * Three leading-wildcard LIKEs, which no B-tree can serve — the same limit
     * §4.1 of the performance report measured on the catalogue. It is acceptable
     * here and not there: this is an administrator's occasional lookup over the
     * smallest of the large tables, not the storefront's primary search box.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String keyword, Integer page, Integer size,
                                             String sortBy, String direction) {

        Pageable pageable = pagination.forUsers(page, size, sortBy, direction);

        Page<User> results = keyword == null || keyword.isBlank()
                ? userRepository.findAll(pageable)
                : userRepository.findAll(matching(keyword.trim()), pageable);

        return PageResponse.from(results, UserResponse::from);
    }

    /**
     * A probe carrying the keyword in every field worth searching.
     *
     * {@code matchingAny} is the whole point — the default is AND, which would
     * only match a user whose username, e-mail <em>and</em> full name all
     * contained the keyword. Null fields are ignored either way, so {@code role},
     * {@code phone} and {@code createdAt} take no part.
     *
     * {@code passwordHash} is listed explicitly under {@code withIgnorePaths}
     * even though it is null on a fresh probe and would be ignored regardless.
     * That line is not doing work today; it is there so that a future change
     * which populates the probe from an existing {@link User} cannot quietly turn
     * this into a query that matches on stored password hashes.
     */
    private Example<User> matching(String keyword) {
        User probe = new User();
        probe.setUsername(keyword);
        probe.setEmail(keyword);
        probe.setFullName(keyword);

        ExampleMatcher matcher = ExampleMatcher.matchingAny()
                .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
                .withIgnoreCase()
                .withIgnorePaths("id", "passwordHash", "phone", "role", "createdAt");

        return Example.of(probe, matcher);
    }

    @Override
    @CacheEvict(value = CacheConfig.PROFILES, key = "#id")
    @Transactional
    public void delete(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));

        // Orders reference users with ON DELETE RESTRICT; reporting the conflict
        // here gives a readable 409 instead of a constraint violation from the
        // driver. An EXISTS, not a load: the previous check read the user's whole
        // order history — every line, every product — to ask whether there was
        // one.
        if (orderRepository.existsByUserId(id))
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
