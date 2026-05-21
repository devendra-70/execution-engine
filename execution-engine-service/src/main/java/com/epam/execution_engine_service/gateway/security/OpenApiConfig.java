package com.epam.execution_engine_service.gateway.security;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Configures SpringDoc / Swagger UI to show an "Authorize" button
 * that attaches a JWT Bearer token to every secured request.
 *
 * <p>How to use in local dev:
 * <ol>
 *   <li>GET /api/dev/token  — grab the {@code token} value from the response.</li>
 *   <li>Click "Authorize" in Swagger UI, paste the token, click Authorize.</li>
 *   <li>All subsequent Swagger requests will include {@code Authorization: Bearer &lt;token&gt;}.</li>
 * </ol>
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title       = "Execution Engine Service API",
                version     = "0.0.1",
                description = "CodEval Execution Engine — REST API. " +
                              "To test secured endpoints: call GET /api/dev/token first, " +
                              "then click the 'Authorize' button and paste the token."
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name        = "bearerAuth",
        type        = SecuritySchemeType.HTTP,
        scheme      = "bearer",
        bearerFormat = "JWT",
        in          = SecuritySchemeIn.HEADER,
        description = "Paste the JWT token obtained from GET /api/dev/token (dev profile only)."
)
public class OpenApiConfig {
    // No beans needed — the annotations above are picked up by springdoc-openapi automatically.
}

