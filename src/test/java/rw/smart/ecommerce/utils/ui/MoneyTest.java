package rw.smart.ecommerce.utils.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Money is always shown with two decimals, whatever scale the value arrives in. */
class MoneyTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0.00",
            "12.5, 12.50",
            "19.99, 19.99",
            "1000, 1000.00",
            "0.005, 0.01"
    })
    void formatsToTwoDecimals(String amount, String expected) {
        assertEquals(expected, Money.format(new BigDecimal(amount)));
    }

    @Test
    @DisplayName("rounding is HALF_UP, matching what a receipt would show")
    void roundsHalfUp() {
        assertEquals("12.35", Money.format(new BigDecimal("12.345")));
        assertEquals("12.34", Money.format(new BigDecimal("12.344")));
    }

    @Test
    void nullBecomesZero() {
        assertEquals("0.00", Money.format(null));
    }

    @Test
    @DisplayName("large values never render in scientific notation")
    void avoidsScientificNotation() {
        assertEquals("1000.00", Money.format(new BigDecimal("1E+3")));
    }
}
