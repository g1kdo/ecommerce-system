package rw.smart.ecommerce.core.product.service;

import rw.smart.ecommerce.core.category.model.Category;

import java.time.LocalDate;

/**
 * Builds the stock keeping unit for a product.
 *
 * Format: {@code CAT-YYMM-NNNNN}
 *
 * <pre>
 *   PER-2608-00042
 *   ^   ^    ^
 *   |   |    +-- product id, zero padded to five digits
 *   |   +------- year and month the product was introduced
 *   +----------- three-letter code derived from the category name
 * </pre>
 *
 * Two properties matter more than the readability.
 *
 * It is <em>unique by construction</em>: the tail is the product's primary key,
 * so no two products can collide and no uniqueness check or retry loop is
 * needed. Deriving it from a counter or a name would need both.
 *
 * It is <em>assigned once</em>. A SKU identifies the physical item on a shelf and
 * appears on historical order lines, so recategorising a product later must not
 * rewrite it — the code embedded in an old SKU records where the product started,
 * which is the honest answer, not a stale one.
 */
public final class SkuGenerator {

    private static final int CODE_LENGTH = 3;
    private static final char PADDING = 'X';

    private SkuGenerator() {
        // utility class, no instances
    }

    public static String generate(Category category, Long productId, LocalDate introducedOn) {
        return "%s-%02d%02d-%05d".formatted(
                categoryCode(category),
                introducedOn.getYear() % 100,
                introducedOn.getMonthValue(),
                productId);
    }

    /**
     * Three letters from the category name — "Home &amp; Kitchen" becomes HOM,
     * "Audio" becomes AUD. Anything that is not a letter is dropped first, so
     * punctuation and digits cannot leak into the code, and short names are
     * padded rather than producing a ragged prefix.
     */
    static String categoryCode(Category category) {
        String name = category == null || category.getName() == null ? "" : category.getName();

        StringBuilder letters = new StringBuilder();
        for (char character : name.toCharArray()) {
            if (Character.isLetter(character)) letters.append(Character.toUpperCase(character));
            if (letters.length() == CODE_LENGTH) break;
        }

        while (letters.length() < CODE_LENGTH) {
            letters.append(PADDING);
        }
        return letters.toString();
    }
}
