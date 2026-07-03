package kg.equeue.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI electronicQueueOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Electronic Queue Backend API")
                        .description("Spring Boot source-of-truth API for auth, directories, tickets, bookings, reports, exports, audit, and device integrations.")
                        .version("v1")
                        .contact(new Contact().name("Electronic Queue Platform"))
                        .license(new License().name("Internal")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local backend"),
                        new Server().url("http://localhost:8088").description("Local nginx gateway")
                ))
                .tags(List.of(
                        new Tag().name("Auth").description("JWT login, refresh, logout, and current user"),
                        new Tag().name("Users").description("User management"),
                        new Tag().name("Roles").description("Role and permission assignment"),
                        new Tag().name("Permissions").description("Permission directory"),
                        new Tag().name("Directories").description("Regions, departments, rooms, halls, windows, services, and assignments"),
                        new Tag().name("Tickets").description("Ticket creation, queue operations, and lifecycle"),
                        new Tag().name("Bookings").description("Online booking, slots, check-in, cancel, and expire"),
                        new Tag().name("Reports").description("Reports, analytics, exports, and downloads"),
                        new Tag().name("Audit").description("Audit log read endpoints"),
                        new Tag().name("Terminal Devices").description("Terminal device endpoints"),
                        new Tag().name("TV Displays").description("TV display endpoints and streams"),
                        new Tag().name("Operator Streams").description("Operator SSE endpoints"),
                        new Tag().name("Health").description("Health checks")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT access token returned by /api/v1/auth/login"))
                        .addSecuritySchemes("backend-integration-key",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Backend-Integration-Key")
                                        .description("Shared internal key forwarded by middleware"))
                        .addSecuritySchemes("integration-client",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Integration-Client")
                                        .description("External client code forwarded by middleware")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
