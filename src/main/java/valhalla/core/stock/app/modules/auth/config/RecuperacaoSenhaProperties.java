package valhalla.core.stock.app.modules.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param urlRedefinicao página do frontend que recebe o token no parâmetro {@code token}
 * @param validade tempo durante o qual o link enviado por e-mail pode ser utilizado
 * @param intervaloMinimoEntreSolicitacoes intervalo mínimo entre e-mails para o mesmo usuário
 */
@Validated
@ConfigurationProperties(prefix = "app.recuperacao-senha")
public record RecuperacaoSenhaProperties(
        @NotBlank String urlRedefinicao,
        @NotNull Duration validade,
        @NotNull Duration intervaloMinimoEntreSolicitacoes
) {
}
