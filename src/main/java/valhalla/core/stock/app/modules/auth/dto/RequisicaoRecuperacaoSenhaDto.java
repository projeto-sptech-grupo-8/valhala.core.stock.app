package valhalla.core.stock.app.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequisicaoRecuperacaoSenhaDto(

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres")
        @Schema(
                description = "E-mail da conta cuja senha será recuperada",
                example = "usuario@exemplo.com"
        )
        String email

) {
}
