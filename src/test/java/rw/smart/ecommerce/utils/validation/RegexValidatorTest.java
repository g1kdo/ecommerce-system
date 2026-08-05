package rw.smart.ecommerce.utils.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Email validation used by the sign-up, sign-in and profile screens. */
class RegexValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "jdoe@example.com",
            "first.last@example.co.uk",
            "user+tag@example.org",
            "user_name@sub.domain.io",
            "a1%b@example.com"
    })
    void acceptsValidAddresses(String email) {
        assertDoesNotThrow(() -> RegexValidator.validateUserEmail(email));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "plainstring",
            "missing@tld",
            "@example.com",
            "spaces in@example.com",
            "trailing@example.com ",
            "two@@example.com",
            "short@example.c"
    })
    void rejectsInvalidAddresses(String email) {
        assertThrows(InvalidInputException.class, () -> RegexValidator.validateUserEmail(email));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsNullAndEmpty(String email) {
        assertThrows(InvalidInputException.class, () -> RegexValidator.validateUserEmail(email));
    }

    @Test
    void explainsTheExpectedFormat() {
        InvalidInputException error = assertThrows(InvalidInputException.class,
                () -> RegexValidator.validateUserEmail("nope"));

        assertTrue(error.getMessage().toLowerCase().contains("email"),
                "the message should tell the user what is wrong, but was: " + error.getMessage());
    }
}
