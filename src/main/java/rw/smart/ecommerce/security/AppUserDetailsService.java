package rw.smart.ecommerce.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.smart.ecommerce.core.user.dao.UserRepository;
import rw.smart.ecommerce.core.user.model.User;

/**
 * Bridges the {@code users} table to Spring Security.
 *
 * Accounts sign in with their e-mail address, which is the unique, stable
 * identifier customers actually remember.
 *
 * Implementing {@link UserDetailsPasswordService} as well is what completes the
 * hash migration: when the encoder reports a stored password as out of date,
 * Spring Security calls {@link #updatePassword} with a freshly BCrypt-hashed
 * value, so legacy SHA-256 rows upgrade silently as their owners log in.
 */
@Slf4j
@Service
public class AppUserDetailsService implements UserDetailsService, UserDetailsPasswordService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Accepts either the e-mail address or the username.
     *
     * E-mail is the canonical identifier and is tried first. Username is accepted
     * as well for a practical reason: every sign-in form in the world, Swagger
     * UI's Authorize dialog included, labels this field "Username" — so an
     * account whose username is {@code admin} will have {@code admin} typed into
     * it. Rejecting that is technically defensible and useless in practice; both
     * values are unique, so accepting either costs nothing.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(login)
                .or(() -> userRepository.findByUsernameIgnoreCase(login))
                // The message is deliberately vague: confirming which addresses
                // exist turns the login form into an account enumerator.
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                // ROLE_ prefix added here so hasRole('ADMIN') matches; the enum
                // itself stays clean.
                .roles(user.getRole().name())
                .build();
    }

    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails userDetails, String newPassword) {
        userRepository.findByEmailIgnoreCase(userDetails.getUsername()).ifPresent(user -> {
            user.setPasswordHash(newPassword);
            userRepository.save(user);
            log.info("Upgraded stored password hash for {} to the current encoder", user.getEmail());
        });

        return org.springframework.security.core.userdetails.User
                .withUserDetails(userDetails)
                .password(newPassword)
                .build();
    }
}
