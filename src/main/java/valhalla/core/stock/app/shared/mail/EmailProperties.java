package valhalla.core.stock.app.shared.mail;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Identificação do remetente dos e-mails enviados pela aplicação. A conexão SMTP é
 * configurada pelas propriedades padrão {@code spring.mail.*}.
 */
@Validated
@ConfigurationProperties(prefix = "app.mail")
public record EmailProperties(
        @NotBlank @Email String remetente,
        @NotBlank String nomeRemetente
) {
}
