package com.interval.apibackend.config;

import java.util.List;
import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Dokumentasi API di /swagger-ui.html dan /v3/api-docs. Semua endpoint ditandai butuh Bearer token, kecuali
 * tiga endpoint auth yang publik. Dimatikan di production lewat springdoc.*.enabled (profil prod).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final Set<String> PUBLIC_PATHS =
            Set.of("/api/auth/register", "/api/auth/login", "/api/auth/refresh");

    @Bean
    public OpenAPI intervalOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Interval API")
                        .version("1.0.0")
                        .description("Backend aplikasi pengingat minum obat Interval. "
                                + "Waktu pada response memakai offset timezone pengguna (ISO 8601); "
                                + "response gagal berformat { success, message, errors }."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    @Bean
    public OpenApiCustomizer publicEndpointsCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            if (PUBLIC_PATHS.contains(path)) {
                item.readOperations().forEach(operation -> operation.setSecurity(List.of()));   // tanpa autentikasi
            }
        });
    }
}
