package rw.smart.ecommerce.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Map;

/**
 * Authentication and authorisation.
 *
 * {@code @EnableMethodSecurity} is the load-bearing line: it turns on the
 * pre-invocation interceptor that {@code @PreAuthorize} depends on. Without it
 * the annotation compiles, reads correctly, and enforces nothing.
 *
 * Scope is deliberately narrow — HTTP Basic over the existing user table, no
 * JWT or OAuth. That is enough to make role checks real without pulling a token
 * infrastructure into the project.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Paths that must stay reachable without credentials. */
    private static final String[] PUBLIC_PATHS = {
            "/",
            "/favicon.ico",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/graphiql/**"
    };

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disabled because this is a stateless API consumed by non-browser
                // clients: there is no session cookie for a forged cross-site
                // request to ride on, so a CSRF token would add ceremony, not safety.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Browsing the catalogue must not require an account.
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**", "/api/v1/categories/**").permitAll()
                        // GraphQL authorises per operation via @PreAuthorize on the
                        // controllers; a blanket rule here would either lock out
                        // public queries or wave admin mutations through.
                        .requestMatchers("/graphql").permitAll()
                        .anyRequest().authenticated())
                // Both handlers exist to keep the browser out of the way and to
                // make filter-level failures look like every other error the API
                // returns. See RestAuthenticationEntryPoint for why the
                // WWW-Authenticate challenge is deliberately not sent.
                .httpBasic(basic -> basic.authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

    /**
     * BCrypt for everything written from now on, with the Phase 1 SHA-256 format
     * still readable so existing accounts are not locked out overnight.
     *
     * The stored value carries its own {@code {id}} prefix, which is what makes
     * the next algorithm change a one-line edit rather than a migration.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        String encodingId = "bcrypt";

        Map<String, PasswordEncoder> encoders = Map.of(
                encodingId, new BCryptPasswordEncoder(),
                "sha256", new LegacySha256PasswordEncoder());

        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder(encodingId, encoders);
        // Rows written before the migration script ran carry no prefix at all;
        // read them as SHA-256 rather than throwing on login.
        encoder.setDefaultPasswordEncoderForMatches(new LegacySha256PasswordEncoder());
        return encoder;
    }
}
