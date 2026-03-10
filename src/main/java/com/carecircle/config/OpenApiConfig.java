package com.carecircle.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

// =============================================================================
// 🧠 SPRINGDOC OPENAPI — What this generates
//
// SpringDoc scans all @RestController classes at startup and auto-generates:
//   /v3/api-docs        → raw OpenAPI 3 JSON spec
//   /swagger-ui.html    → interactive Swagger UI
//
// SecurityConfig already has .permitAll() for /swagger-ui/** and /v3/api-docs/**
// so no auth is needed to view the docs.
//
// Security scheme "CookieAuth" tells Swagger UI to send the access_token cookie
// automatically when you click "Authorize". This lets you test authenticated
// endpoints directly from the browser without Postman.
// =============================================================================

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "CareCircle API",
                version = "1.0",
                description = """
            Production-grade family caregiving coordination platform.
            
            **Authentication:** Google OAuth2 login at `/oauth2/authorization/google`.
            After login, the server sets an `access_token` HttpOnly cookie automatically.
            All `/api/v1/**` endpoints require this cookie.
            
            **Rate Limiting:** 100 requests per minute per authenticated user.
            Exceeding the limit returns HTTP 429 with `Retry-After: 60 seconds`.
            """,
                contact = @Contact(name = "CareCircle Backend", url = "https://github.com/dev-yash05/carecircle-backend")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local development"),
        },
        security = @SecurityRequirement(name = "CookieAuth")
)
@SecurityScheme(
        name = "CookieAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = "access_token",
        description = "JWT access token set by the server after Google OAuth2 login. " +
                "Log in via /oauth2/authorization/google first, then this is sent automatically."
)
public class OpenApiConfig {
    // No bean methods needed — @OpenAPIDefinition + @SecurityScheme on the class
    // is enough for SpringDoc to pick up the configuration.
}