package com.inventra.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger configuration.
 *
 * Server URLs are intentionally omitted here so SpringDoc auto-detects
 * the base URL at runtime (works for both dev and production).
 * In production the swagger-ui is disabled entirely via application-prod.yml.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Inventra API",
                version = "1.0",
                description = """
                        Multi-tenant Inventory and Order Management System
                        
                        ## Authentication
                        1. Register a new tenant using POST /api/v1/auth/register
                        2. Login using POST /api/v1/auth/login to get access and refresh tokens
                        3. Use the access token in the Authorization header: Bearer {token}
                        4. Refresh tokens using POST /api/v1/auth/refresh when access token expires
                        
                        ## Multi-Tenancy
                        All data is automatically scoped to your tenant based on the JWT token.
                        You can only access data belonging to your tenant.
                        
                        ## Roles
                        - **ADMIN**: Full access to all operations
                        - **MANAGER**: Can manage products, categories, and view users
                        - **WAREHOUSE_STAFF**: Can view and manage inventory
                        - **VIEWER**: Read-only access
                        """,
                contact = @Contact(
                        name = "Inventra API Support",
                        email = "support@inventra.com"
                )
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT authentication token obtained from /api/v1/auth/login"
)
public class OpenApiConfig {
}
