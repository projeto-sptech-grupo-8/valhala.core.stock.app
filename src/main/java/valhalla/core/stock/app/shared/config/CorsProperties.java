package valhalla.core.stock.app.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Origens autorizadas a consumir a API (CORS) e a enviar requisições com estado (CSRF).
 *
 * <p>Uma única propriedade aceita várias origens separadas por vírgula, o que mantém
 * o CORS e a validação de CSRF consistentes e permite ambientes com mais de um host.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException(
                    "app.cors.allowed-origins (FRONTEND_URL) deve conter ao menos uma origem"
            );
        }
        allowedOrigins = allowedOrigins.stream()
                .map(CorsProperties::validarOrigem)
                .toList();
    }

    /** Comparação null-safe, pois o header Origin é opcional em várias requisições. */
    public boolean allows(String origin) {
        return origin != null && allowedOrigins.contains(origin);
    }

    private static String validarOrigem(String origem) {
        if (origem == null || origem.isBlank()) {
            throw new IllegalArgumentException(
                    "app.cors.allowed-origins (FRONTEND_URL) não aceita valores vazios"
            );
        }
        String valor = origem.trim();
        URI uri;
        try {
            uri = new URI(valor);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "app.cors.allowed-origins (FRONTEND_URL) contém uma URL inválida: " + valor,
                    exception
            );
        }

        boolean esquemaValido = "http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme());
        if (!esquemaValido || uri.getHost() == null) {
            throw new IllegalArgumentException(
                    "app.cors.allowed-origins (FRONTEND_URL) deve ser uma origem absoluta http(s), "
                            + "ex.: https://app.meraki.com: " + valor
            );
        }
        if (!uri.getPath().isEmpty() || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "app.cors.allowed-origins (FRONTEND_URL) não deve conter caminho, query "
                            + "nem barra final: " + valor
            );
        }
        return valor;
    }
}
