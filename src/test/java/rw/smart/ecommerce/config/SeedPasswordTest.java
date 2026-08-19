package rw.smart.ecommerce.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that keeps a password out of the repository.
 *
 * A plain unit test — no Spring context, so it runs without a database, which
 * suits the one class in this codebase whose whole job is to have no default.
 */
@DisplayName("Seed password resolution")
class SeedPasswordTest {

    @ParameterizedTest(name = "configured = [{0}]")
    @NullSource
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("generates one when nothing is configured, and allows it to be printed")
    void generatesWhenUnset(String configured) {
        SeedPassword password = SeedPassword.resolve(configured);

        assertFalse(password.value().isBlank(), "an unset property must not yield a blank password");
        assertTrue(password.isPrintable(),
                "a generated password nobody can read is a locked account, so it has to be logged");
        assertEquals(password.value(), password.forDisplay());
    }

    @Test
    @DisplayName("uses a configured password verbatim and refuses to print it")
    void usesConfiguredWithoutPrinting() {
        SeedPassword password = SeedPassword.resolve("chosen-by-the-operator");

        assertEquals("chosen-by-the-operator", password.value());
        assertFalse(password.isPrintable(),
                "an operator may reuse this password elsewhere; log files outlive terminals");
        assertFalse(password.forDisplay().contains("chosen-by-the-operator"),
                "forDisplay is what reaches the log, and it must not leak the value");
    }

    @Test
    @DisplayName("a generated password is different every time")
    void generatedPasswordsAreNotReused() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) seen.add(SeedPassword.resolve(null).value());

        // The failure this guards against is a "random" default that is really a
        // constant - which is what the literal it replaced amounted to.
        assertEquals(100, seen.size(), "every run should get its own password");
    }

    @Test
    @DisplayName("a generated password is long enough that guessing is not a plan")
    void generatedPasswordIsLongEnough() {
        String value = SeedPassword.resolve(null).value();

        assertTrue(value.length() >= 20, "18 random bytes should Base64 to 24 characters, got " + value.length());
        assertNotEquals(value.toLowerCase(), value, "Base64url is mixed case, so this should not be all lower");
    }
}
