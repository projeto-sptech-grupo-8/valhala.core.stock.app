package valhalla.core.stock.app.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Atributos dos cookies de autenticação.
 *
 * <p>O padrão é seguro (exigir HTTPS) para que um ambiente publicado sem
 * configuração explícita não trafegue tokens em texto claro.
 */
@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(@DefaultValue("true") boolean secure) {
}
