package rw.smart.ecommerce.config;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Supplies the password for a seeded account.
 *
 * <h4>Why this exists</h4>
 *
 * The seeder used to carry a literal default password as the
 * fallback on {@code @Value}, repeated in the sample-user list, printed in the
 * startup log, published in the OpenAPI document, and written out in two
 * markdown files. That is a known administrator password for every deployment
 * that never set the property, documented publicly, and reachable by anyone who
 * reads the repository.
 *
 * A default password is worse than no default. It is guessable precisely because
 * it is documented, and the documentation is what makes it convenient enough that
 * nobody changes it.
 *
 * <h4>What happens instead</h4>
 *
 * If the operator configures a password, that one is used and it is never
 * logged — a password an operator chose may well be one they use elsewhere.
 *
 * If they do not, one is generated from {@link SecureRandom} for this run only,
 * and <em>that</em> one is printed once at startup, because a generated
 * credential nobody can read is a locked account. This is the same bargain Spring
 * Boot itself strikes with its default security user.
 *
 * The seeder never runs under the {@code prod} profile, so neither branch can put
 * a generated password into a production log.
 */
final class SeedPassword {

    /** 18 bytes -> 24 Base64 characters. Long enough that guessing is not a plan. */
    private static final int RANDOM_BYTES = 18;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String value;
    private final boolean generated;

    private SeedPassword(String value, boolean generated) {
        this.value = value;
        this.generated = generated;
    }

    /**
     * @param configured the value of {@code app.seed.*-password}, blank if unset
     */
    static SeedPassword resolve(String configured) {
        if (configured != null && !configured.isBlank()) return new SeedPassword(configured, false);

        return new SeedPassword(
                Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes()), true);
    }

    String value() {
        return value;
    }

    /**
     * Whether this password may be written to the log.
     *
     * True only for a generated one. A configured password stays out of the log
     * entirely — the operator already knows it, and log files outlive the
     * terminal somebody read them in.
     */
    boolean isPrintable() {
        return generated;
    }

    /** What to show where the password would otherwise go. */
    String forDisplay() {
        return generated ? value : "(as configured in app.seed.*-password)";
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
