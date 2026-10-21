package com.pedidos360.admin.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Swagger UI en /swagger-ui.html. Botón "Authorize": pegar el ID token de Cognito (usuario ADMIN). */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Pedidos360 · Mensajería Admin API", version = "1.0",
                description = "Administración de la topología SNS/SQS: colas, tópicos, suscripciones, DLQ y consumidores."),
        security = @SecurityRequirement(name = "cognito"))
@SecurityScheme(name = "cognito", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
