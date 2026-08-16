package rw.smart.ecommerce.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * What the API answers when a request arrives with no usable credentials.
 *
 * The default {@code BasicAuthenticationEntryPoint} sends
 * {@code WWW-Authenticate: Basic realm="Realm"} with an empty body. That header is
 * a browser instruction, and browsers obey it: Chrome intercepts the 401 and
 * opens its own native sign-in dialog before any JavaScript on the page can see
 * the response. In Swagger UI the effect is that the browser prompt appears
 * *instead of* the request completing, and whatever is typed into it is not what
 * Swagger's own "Authorize" button manages — so the call appears to hang.
 *
 * Omitting the challenge header fixes that. The status is unchanged, so every
 * non-browser client behaves exactly as before ({@code curl -u} sends Basic
 * credentials preemptively and never needed the challenge), and Swagger UI is
 * free to attach the Authorization header itself.
 *
 * The body carries the same {@code StandardResponse} envelope as every other
 * error, so a 401 raised down here by the filter chain is indistinguishable, to a
 * client, from one raised by the controller advice.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String BODY = """
            {"status":401,"message":"Authentication required. \
            Send your account e-mail and password using HTTP Basic authentication."}""";

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(BODY);
    }
}
