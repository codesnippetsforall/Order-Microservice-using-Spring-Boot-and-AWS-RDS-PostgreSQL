package com.winsoon.orderms.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger configuration.
 * Customises the metadata shown on the Swagger UI page.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderMsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Microservice API")
                        .description("REST API for managing customers, orders and order items.")
                        .version("1.0.0")
                        .contact(new Contact().name("Winsoon OrderMS"))
                        .license(new License().name("Apache 2.0")));
    }
}
