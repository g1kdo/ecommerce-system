package rw.smart.ecommerce.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Denials raised by the filter chain rather than by {@code @PreAuthorize}.
 *
 * The controller advice already covers method security, but it never sees a
 * refusal that happens before the request reaches a handler. Without this, those
 * are answered with an empty body and a container-generated error page — a
 * different shape from every other error the API produces.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final String BODY =
            """
            {"status":403,"message":"You do not have permission to perform this action."}""";

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(BODY);
    }
}
