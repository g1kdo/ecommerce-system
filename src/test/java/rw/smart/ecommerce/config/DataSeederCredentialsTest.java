package rw.smart.ecommerce.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import rw.smart.ecommerce.core.user.dao.UserRepository;
import rw.smart.ecommerce.core.user.enums.UserRole;
import rw.smart.ecommerce.core.user.model.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The path the other suites never take: seeding into an empty database.
 *
 * {@code application-test.properties} turns the seeder off, because tests own
 * their fixtures. That leaves the branch where the bootstrap administrator is
 * actually created — and therefore the branch where the password rule matters —
 * unexercised. This class turns seeding back on for one context and checks the
 * account that comes out.
 *
 * A password is configured here rather than generated, so the test can assert
 * against a known value. The generated branch is covered by
 * {@link SeedPasswordTest}, which needs no database.
 *
 * <h4>Requires PostgreSQL</h4>
 *
 * <pre>createdb -U postgres smart_ecommerce_test_db</pre>
 */
@SpringBootTest(properties = {
        "app.seed.enabled=true",
        "app.seed.admin-email=seed-test-admin@example.test",
        "app.seed.admin-password=configured-for-this-test",
        "app.seed.sample-password=sample-for-this-test"
})
@ActiveProfiles("test")
@DisplayName("Seeded account credentials")
class DataSeederCredentialsTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("the bootstrap administrator is created with the configured password, BCrypt-hashed")
    void bootstrapAdminUsesTheConfiguredPassword() {
        User admin = userRepository.findByEmailIgnoreCase("seed-test-admin@example.test")
                .orElseThrow(() -> new AssertionError("the seeder should have created the bootstrap admin"));

        assertEquals(UserRole.ADMIN, admin.getRole());

        assertTrue(passwordEncoder.matches("configured-for-this-test", admin.getPasswordHash()),
                "the configured password should be what the account actually authenticates with");

        assertFalse(admin.getPasswordHash().contains("configured-for-this-test"),
                "it must be stored hashed, never in a form the column reveals");
    }

    @Test
    @DisplayName("sample customers use the sample password, not the administrator's")
    void sampleUsersAreSeparateFromTheAdmin() {
        User customer = userRepository.findByEmailIgnoreCase("k.mugisha@example.com")
                .orElseThrow(() -> new AssertionError("the seeder should have created the sample customers"));

        assertEquals(UserRole.CUSTOMER, customer.getRole());

        assertTrue(passwordEncoder.matches("sample-for-this-test", customer.getPasswordHash()));

        // The two are separate properties on purpose. Handing demo accounts the
        // administrator's password would mean anyone given a demo login could
        // sign in as the administrator too.
        assertFalse(passwordEncoder.matches("configured-for-this-test", customer.getPasswordHash()),
                "a sample account must not accept the administrator password");
    }
}
