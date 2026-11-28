package valhalla.core.stock.app.modules.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Base64;

/**
 * Configuração dos tokens JWT.
 *
 * <p>As validações do construtor compacto falham na inicialização da aplicação,
 * em vez de deixar um ambiente subir com uma configuração insegura.
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("PT1H") Duration expiration,
        @DefaultValue("P7D") Duration refreshExpiration,
        @DefaultValue("valhalla-core-stock") String issuer,
        @DefaultValue("valhalla-core-stock-api") String audience
) {

    public static final String DEFAULT_ISSUER = "valhalla-core-stock";
    public static final String DEFAULT_AUDIENCE = "valhalla-core-stock-api";

    /** Tamanho mínimo exigido pela assinatura HS256. */
    private static final int TAMANHO_MINIMO_SECRET_EM_BYTES = 32;

    @ConstructorBinding
    public JwtProperties {
        secret = validarSecret(secret);
        expiration = validarDuracao(expiration, "security.jwt.expiration (JWT_EXPIRATION)");
        refreshExpiration = validarDuracao(
                refreshExpiration,
                "security.jwt.refresh-expiration (JWT_REFRESH_EXPIRATION)"
        );
        if (refreshExpiration.compareTo(expiration) <= 0) {
            throw new IllegalArgumentException(
                    "security.jwt.refresh-expiration (JWT_REFRESH_EXPIRATION) deve ser maior que "
                            + "security.jwt.expiration (JWT_EXPIRATION)"
            );
        }
        issuer = validarIdentificador(issuer, DEFAULT_ISSUER, "security.jwt.issuer (JWT_ISSUER)");
        audience = validarIdentificador(audience, DEFAULT_AUDIENCE, "security.jwt.audience (JWT_AUDIENCE)");
    }

    public JwtProperties(String secret, Duration expiration, Duration refreshExpiration) {
        this(secret, expiration, refreshExpiration, DEFAULT_ISSUER, DEFAULT_AUDIENCE);
    }

    /** Chave HMAC derivada do secret já validado, pronta para uso pelos beans de JWT. */
    public SecretKey secretKey() {
        return new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
    }

    private static String validarSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                    "security.jwt.secret (JWT_SECRET) é obrigatório. "
                            + "Gere um valor Base64 com pelo menos 32 bytes; veja docs/CONFIGURACAO.md"
            );
        }
        if (secret.contains("COLOQUE") || secret.contains("TROQUE")) {
            throw new IllegalArgumentException(
                    "security.jwt.secret (JWT_SECRET) ainda contém um valor de exemplo. "
                            + "Gere um novo secret para cada ambiente; veja docs/CONFIGURACAO.md"
            );
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "security.jwt.secret (JWT_SECRET) deve ser Base64 válido, sem espaços, "
                            + "quebras de linha ou aspas; veja docs/CONFIGURACAO.md",
                    exception
            );
        }
        if (decoded.length < TAMANHO_MINIMO_SECRET_EM_BYTES) {
            throw new IllegalArgumentException(
                    "security.jwt.secret (JWT_SECRET) deve representar pelo menos "
                            + TAMANHO_MINIMO_SECRET_EM_BYTES + " bytes (256 bits)"
            );
        }
        return secret;
    }

    private static Duration validarDuracao(Duration duracao, String propriedade) {
        if (duracao == null || duracao.isZero() || duracao.isNegative()) {
            throw new IllegalArgumentException(
                    propriedade + " deve ser uma duração positiva (ex.: PT1H ou 1h)"
            );
        }
        return duracao;
    }

    private static String validarIdentificador(String valor, String padrao, String propriedade) {
        if (valor == null) {
            return padrao;
        }
        String trimmed = valor.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(propriedade + " não pode ficar em branco");
        }
        return trimmed;
    }
}
