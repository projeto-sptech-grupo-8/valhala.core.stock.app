package valhalla.core.stock.app.modules.caixa.fiscal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarNotaFiscalRequestDto(
        @Schema(
                description = "Justificativa do cancelamento fiscal (mínimo de 15 caracteres exigido pelo padrão SEFAZ)",
                example = "Erro no preenchimento do documento do consumidor na emissão"
        )
        @NotBlank(message = "O motivo do cancelamento é obrigatório")
        @Size(min = 15, max = 255, message = "O motivo do cancelamento deve ter entre 15 e 255 caracteres")
        String motivo,

        @Schema(
                description = "E-mail do gerente/autorizador quando o operador atual não possui CAIXA_CANCELAR ou CAIXA_ESTORNAR",
                nullable = true
        )
        String autorizadorEmail,

        @Schema(
                description = "Senha do gerente/autorizador",
                nullable = true
        )
        String autorizadorSenha
) {
}
