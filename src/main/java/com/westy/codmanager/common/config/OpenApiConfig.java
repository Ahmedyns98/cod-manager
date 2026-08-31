package com.westy.codmanager.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI codManagerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("COD Manager API")
                .version("v1")
                .description("Cash-on-delivery order management for Algerian sellers"));
    }
}
