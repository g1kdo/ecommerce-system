package rw.smart.ecommerce.security;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Reads — and only reads — the unsalted SHA-256 hex hashes written by Phase 1.
 *
 * It exists so existing accounts keep working through the switch to BCrypt. It
 * is registered under the {@code {sha256}} prefix and reports
 * {@link #upgradeEncoding()} as {@code true}, so the first successful sign-in
 * with one of these hashes rewrites it as BCrypt and the legacy form disappears
 * from the database account by account.
 *
 * {@link #encode} deliberately throws: nothing may create a new hash in this
 * format, or the migration would never finish.
 */
public class LegacySha256PasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
        throw new UnsupportedOperationException(
                "SHA-256 is read-only legacy support; new passwords are hashed with BCrypt.");
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) return false;

        // Constant-time comparison: a length-sensitive String.equals leaks how much
        // of a candidate hash was correct.
        return MessageDigest.isEqual(
                sha256Hex(rawPassword).getBytes(StandardCharsets.UTF_8),
                encodedPassword.getBytes(StandardCharsets.UTF_8));
    }

    /** Always true, so Spring Security re-hashes on the next successful login. */
    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return true;
    }

    private String sha256Hex(CharSequence rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(rawPassword.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
