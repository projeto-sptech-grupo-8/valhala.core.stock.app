package valhalla.core.stock.app.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import valhalla.core.stock.app.modules.auth.config.JwtProperties;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Garante que o vínculo das propriedades aceita valores no formato usado por
 * variáveis de ambiente e aplica os padrões documentados.
 */
class ConfiguracaoPropertiesBindingTest {

    private static final String SECRET_VALIDO =
            "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    @Test
    void deveDividirOrigensSeparadasPorVirgula() {
        new ApplicationContextRunner()
                .withUserConfiguration(CorsHabilitado.class)
                .withPropertyValues(
                        "app.cors.allowed-origins=http://localhost:5173, https://app.meraki.com")
                .run(context -> {
                    CorsProperties properties = context.getBean(CorsProperties.class);

                    assertEquals(2, properties.allowedOrigins().size());
                    assertTrue(properties.allows("https://app.meraki.com"));
                });
    }

    @Test
    void deveExigirCookiesSegurosQuandoNaoConfigurado() {
        new ApplicationContextRunner()
                .withUserConfiguration(CookieHabilitado.class)
                .run(context -> assertTrue(context.getBean(CookieProperties.class).secure()));
    }

    @Test
    void deveAplicarDuracoesPadraoDoJwt() {
        new ApplicationContextRunner()
                .withUserConfiguration(JwtHabilitado.class)
                .withPropertyValues("security.jwt.secret=" + SECRET_VALIDO)
                .run(context -> {
                    JwtProperties properties = context.getBean(JwtProperties.class);

                    assertEquals(Duration.ofHours(1), properties.expiration());
                    assertEquals(Duration.ofDays(7), properties.refreshExpiration());
                });
    }

    @Test
    void deveFalharAoIniciarComSecretDeExemplo() {
        new ApplicationContextRunner()
                .withUserConfiguration(JwtHabilitado.class)
                .withPropertyValues("security.jwt.secret=COLOQUE_AQUI_UM_SECRET_BASE64")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtHabilitado {
    }

    @EnableConfigurationProperties(CorsProperties.class)
    static class CorsHabilitado {
    }

    @EnableConfigurationProperties(CookieProperties.class)
    static class CookieHabilitado {
    }
}
