package com.westy.codmanager.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI codManagerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("COD Manager API")
                        .version("v1")
                        .description("""
                                Cash-on-delivery order management for Algerian sellers.

                                Register or log in, then paste the returned token into
                                Authorize to try the protected endpoints.""")
                        .contact(new Contact().name("Westy"))
                        .license(new License().name("MIT")))
                /* Declaring the scheme is what makes Swagger's Authorize button
                   send the Authorization header on every request. */
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
