package rw.smart.ecommerce.core.user;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import rw.smart.ecommerce.core.user.dao.UserRepository;
import rw.smart.ecommerce.core.user.dto.UserResponse;
import rw.smart.ecommerce.core.user.enums.UserRole;
import rw.smart.ecommerce.core.user.model.User;
import rw.smart.ecommerce.core.user.service.UserService;
import rw.smart.ecommerce.utils.response.PageResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The administrator user search, which is the one place Query by Example is used.
 *
 * The interesting case is {@link #matchesOnUsername()}: the derived method this
 * replaced searched full name and e-mail only, so an administrator looking a user
 * up by the username shown on every screen got nothing back.
 *
 * <h4>Requires PostgreSQL</h4>
 *
 * <pre>createdb -U postgres smart_ecommerce_test_db</pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("User search by Example probe")
class UserSearchQueryByExampleTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private String tag;
    private final List<Long> created = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 8);

        save("zulu" + tag, "quiet.person-" + tag + "@example.test", "Beatrice Uwase");
        save("kmugisha" + tag, "k.mugisha-" + tag + "@example.test", "Kevine Mugisha");
        save("unrelated" + tag, "nobody-" + tag + "@example.test", "Someone Else");
    }

    @AfterEach
    void tearDown() {
        created.forEach(userRepository::deleteById);
        created.clear();
    }

    @Test
    @DisplayName("matches on username — which the derived method it replaced could not do")
    void matchesOnUsername() {
        PageResponse<UserResponse> results = userService.search("kmugisha" + tag, 0, 20, "id", "ASC");

        assertEquals(1, results.totalElements());
        assertEquals("Kevine Mugisha", results.content().getFirst().fullName());
    }

    @Test
    @DisplayName("matches on full name, case-insensitively and on a substring")
    void matchesOnPartialFullName() {
        PageResponse<UserResponse> results = userService.search("BEATRICE", 0, 20, "id", "ASC");

        assertTrue(results.content().stream().anyMatch(user -> "Beatrice Uwase".equals(user.fullName())),
                "the matcher is CONTAINING and ignore-case, so this should hit");
    }

    @Test
    @DisplayName("matches on e-mail")
    void matchesOnEmail() {
        PageResponse<UserResponse> results = userService.search("quiet.person-" + tag, 0, 20, "id", "ASC");

        assertEquals(1, results.totalElements());
        assertEquals("Beatrice Uwase", results.content().getFirst().fullName());
    }

    @Test
    @DisplayName("the probe fields are OR'd, not AND'd")
    void probeFieldsAreOred() {
        // "Mugisha" is in the full name and the e-mail but not the username, and
        // "zulu" is in a username only. Under the default matchingAll() both of
        // these would return nothing, because no single row contains the keyword
        // in all three fields.
        assertEquals(1, userService.search("Mugisha", 0, 20, "id", "ASC").content().stream()
                .filter(user -> user.username().endsWith(tag))
                .count());

        assertEquals(1, userService.search("zulu" + tag, 0, 20, "id", "ASC").totalElements());
    }

    @Test
    @DisplayName("a blank keyword lists everyone rather than building an empty probe")
    void blankKeywordListsEveryone() {
        long all = userRepository.count();

        assertEquals(all, userService.search("   ", 0, 5, "id", "ASC").totalElements());
        assertEquals(all, userService.search(null, 0, 5, "id", "ASC").totalElements());
    }

    @Test
    @DisplayName("a keyword nobody matches returns an empty page, not everybody")
    void unmatchedKeywordReturnsNothing() {
        assertEquals(0, userService.search("no-such-user-" + UUID.randomUUID(), 0, 20, "id", "ASC")
                .totalElements());
    }

    private void save(String username, String email, String fullName) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("not-a-real-hash");
        user.setFullName(fullName);
        user.setRole(UserRole.CUSTOMER);

        created.add(userRepository.save(user).getId());
    }
}
