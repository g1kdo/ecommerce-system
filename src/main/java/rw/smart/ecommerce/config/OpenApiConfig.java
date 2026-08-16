package rw.smart.ecommerce.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Document-level OpenAPI metadata. The per-endpoint descriptions live on the
 * controllers as {@code @Operation}, next to the code they describe, where they
 * are far more likely to be kept honest.
 *
 * Declaring the HTTP Basic scheme here is what puts a working "Authorize" button
 * in Swagger UI — otherwise every secured endpoint reads as a 401 that the
 * reader has no way to get past.
 */
@Configuration
public class OpenApiConfig {

    private static final String BASIC_AUTH = "basicAuth";

    @Bean
    public OpenAPI ecommerceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Smart E-Commerce System API")
                        .version("v1")
                        .description("""
                                REST and GraphQL API for the Smart E-Commerce System.

                                Endpoints marked *(Admin only)* require an account with the ADMIN
                                role and are enforced by @PreAuthorize on the service entry point.
                                Catalogue reads are public.""")
                        .contact(new Contact().name("Smart E-Commerce Team")))
                .components(new Components().addSecuritySchemes(BASIC_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("""
                                        Use the Authorize button, not the browser's own popup.
                                        Username field: your account e-mail address or your username \
                                        (either works). Seeded administrator: \
                                        admin@smartecommerce.rw / Admin@12345""")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
