package valhalla.core.stock.app.modules.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        Duration expiration,
        Duration refreshExpiration
) {
    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("security.jwt.secret é obrigatório");
        }
        if (expiration == null || expiration.isNegative() || expiration.isZero()) {
            throw new IllegalArgumentException("security.jwt.expiration deve ser positiva");
        }
        if (refreshExpiration == null
                || refreshExpiration.isNegative()
                || refreshExpiration.isZero()) {
            throw new IllegalArgumentException(
                    "security.jwt.refresh-expiration deve ser positiva"
            );
        }
    }
}
