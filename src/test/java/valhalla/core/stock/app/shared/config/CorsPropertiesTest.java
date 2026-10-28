package valhalla.core.stock.app.shared.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsPropertiesTest {

    @Test
    void deveAceitarUmaOuMaisOrigens() {
        CorsProperties properties = new CorsProperties(List.of(
                "http://localhost:5173",
                "https://app.meraki.com"
        ));

        assertEquals(2, properties.allowedOrigins().size());
        assertTrue(properties.allows("https://app.meraki.com"));
        assertFalse(properties.allows("https://outro.site.com"));
    }

    @Test
    void deveTratarEspacosEAusenciaDeOrigin() {
        CorsProperties properties = new CorsProperties(List.of(" http://localhost:5173 "));

        assertTrue(properties.allows("http://localhost:5173"));
        assertFalse(properties.allows(null));
    }

    @Test
    void deveRejeitarListaVazia() {
        assertThrows(IllegalArgumentException.class, () -> new CorsProperties(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CorsProperties(null));
        assertThrows(IllegalArgumentException.class, () -> new CorsProperties(List.of(" ")));
    }

    @Test
    void deveRejeitarBarraFinalECaminho() {
        assertThrows(IllegalArgumentException.class,
                () -> new CorsProperties(List.of("http://localhost:5173/")));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsProperties(List.of("https://app.meraki.com/api")));
    }

    @Test
    void deveRejeitarOrigemSemEsquemaHttp() {
        assertThrows(IllegalArgumentException.class,
                () -> new CorsProperties(List.of("localhost:5173")));
        assertThrows(IllegalArgumentException.class,
                () -> new CorsProperties(List.of("ftp://app.meraki.com")));
    }
}
