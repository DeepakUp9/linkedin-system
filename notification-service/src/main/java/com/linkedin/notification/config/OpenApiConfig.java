package com.linkedin.notification.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) configuration for API documentation.
 * 
 * Access Swagger UI:
 * - http://localhost:8083/swagger-ui.html
 * 
 * Access OpenAPI JSON:
 * - http://localhost:8083/v3/api-docs
 * 
 * Features:
 * - Interactive API documentation
 * - Try out endpoints directly in browser
 * - JWT authentication support
 * - Request/response examples
 * 
 * @see com.linkedin.notification.controller.NotificationController
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Notification Service API",
        version = "1.0",
        description = "REST API for managing user notifications, preferences, and delivery channels",
        contact = @Contact(
            name = "LinkedIn System Team",
            email = "support@linkedin.com"
        )
    ),
    servers = {
        @Server(url = "http://localhost:8083", description = "Local Development Server"),
        @Server(url = "https://api.linkedin.com", description = "Production Server")
    }
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT authentication token. Obtain from /api/auth/login endpoint in user-service."
)
public class OpenApiConfig {
}

