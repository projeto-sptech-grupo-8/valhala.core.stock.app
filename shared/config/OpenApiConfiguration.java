package valhalla.core.stock.app.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI valhallaOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Valhalla Core Stock API")
                        .version("v1")
                        .description("API de autenticação, usuários, perfis e permissões."))
                .components(new Components().addSecuritySchemes("accessTokenCookie",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE).name("accessToken")
                                .description("Cookie HttpOnly criado por POST /auth/login.")));
    }
}
