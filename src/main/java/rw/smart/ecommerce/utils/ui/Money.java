package rw.smart.ecommerce.utils.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single place where money is turned into display text, so totals never render
 * as "0" or "12.5" next to a column of two-decimal amounts.
 */
public final class Money {

    private Money() {
        // utility class, no instances
    }

    public static String format(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
