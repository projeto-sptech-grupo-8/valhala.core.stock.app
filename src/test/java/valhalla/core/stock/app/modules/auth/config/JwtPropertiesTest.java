package valhalla.core.stock.app.modules.auth.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtPropertiesTest {

    private static final String SECRET_VALIDO =
            "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    @Test
    void deveAceitarConfiguracaoValida() {
        JwtProperties properties = new JwtProperties(
                SECRET_VALIDO,
                Duration.ofHours(1),
                Duration.ofDays(7)
        );

        assertEquals("HmacSHA256", properties.secretKey().getAlgorithm());
        assertEquals(32, properties.secretKey().getEncoded().length);
    }

    @Test
    void deveRejeitarSecretAusente() {
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(
                null, Duration.ofHours(1), Duration.ofDays(7)));
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(
                "   ", Duration.ofHours(1), Duration.ofDays(7)));
    }

    @Test
    void deveRejeitarSecretDeExemplo() {
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(
                "COLOQUE_AQUI_UM_SECRET_BASE64_COM_PELO_MENOS_32_BYTES",
                Duration.ofHours(1),
                Duration.ofDays(7)
        ));
    }

    @Test
    void deveRejeitarSecretForaDoBase64() {
        // Aspas são o erro mais comum ao copiar valores para um .env lido como .properties.
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(
                "\"" + SECRET_VALIDO + "\"",
                Duration.ofHours(1),
                Duration.ofDays(7)
        ));
    }

    @Test
    void deveRejeitarSecretComMenosDe32Bytes() {
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(
                "c2VuaGEtY3VydGE=",
                Duration.ofHours(1),
                Duration.ofDays(7)
        ));
    }

    @Test
    void deveRejeitarRefreshMenorQueAccess() {
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(
                SECRET_VALIDO,
                Duration.ofDays(7),
                Duration.ofHours(1)
        ));
    }

    @Test
    void deveRejeitarDuracoesNaoPositivas() {
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(
                SECRET_VALIDO, Duration.ZERO, Duration.ofDays(7)));
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(
                SECRET_VALIDO, Duration.ofHours(1), Duration.ofDays(-1)));
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(
                SECRET_VALIDO, null, Duration.ofDays(7)));
    }
}
