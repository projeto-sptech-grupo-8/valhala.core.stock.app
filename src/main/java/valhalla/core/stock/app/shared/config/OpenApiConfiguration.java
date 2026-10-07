package valhalla.core.stock.app.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI valhallaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Valhalla Core Stock API")
                        .version("v1")
                        .description("""
                                API de autenticação, usuários, perfis, permissões e estoque.

                                Operações autenticadas que alteram estado exigem o header X-XSRF-TOKEN.
                                Após login ou refresh, o frontend deve chamar GET /auth/csrf usando o cookie
                                HttpOnly accessToken e enviar o campo token retornado nesse header. O token CSRF
                                é controlado pelo backend e não é armazenado em cookie.
                                """))
                .components(new Components().addSecuritySchemes("accessTokenCookie",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("accessToken")
                                .description("Cookie HttpOnly criado por POST /auth/login."))
                        .addSecuritySchemes("csrfTokenHeader",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-XSRF-TOKEN")
                                        .description("Obrigatório em POST, PUT, PATCH e DELETE autenticados.")));
    }
}
