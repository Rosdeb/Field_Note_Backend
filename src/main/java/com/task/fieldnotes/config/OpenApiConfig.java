package com.task.fieldnotes.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean OpenAPI fieldNotesApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Field Notes API")
                .version("1.0.0")
                .description("Complete API documentation for Field Notes: Customer, Site, Field Note management, Photo storage, and Offline Two-Way Synchronization.")
                .contact(new Contact().name("Field Notes Team")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Localhost (Web / General)"),
                new Server().url("http://10.0.2.2:8080").description("Android Emulator Localhost"),
                new Server().url("http://127.0.0.1:8080").description("iOS Simulator / macOS Localhost")
            ))
            .tags(List.of(
                new Tag().name("Authentication").description("Registration, login, JWT token refresh, profile, and logout"),
                new Tag().name("Synchronization").description("Offline two-way synchronization: push local mutation batches and pull incremental server change revisions"),
                new Tag().name("Data Resources").description("Query, read, upsert, and soft-delete operations for Customers, Sites, and Field Notes"),
                new Tag().name("Photos").description("Inspection photo uploads and downloads (up to 5 MiB binary JPEG/PNG)")
            ))
            .components(new Components().addSecuritySchemes("bearerAuth",
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                    .description("Paste the JWT access token returned by /api/auth/register, /api/auth/login, or /api/auth/refresh.")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean OpenApiCustomizer publicAuthenticationEndpoints() {
        return api -> {
            for(String path : new String[]{"/api/auth/register", "/api/auth/login", "/api/auth/refresh"}) {
                var item = api.getPaths().get(path);
                if(item != null && item.getPost() != null) item.getPost().setSecurity(java.util.List.of());
            }
        };
    }
}
