package valhalla.core.stock.app.modules.caixa.pedido.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecisaoDescontoPedidoRequestDto(
        @NotNull(message = "Informe se o desconto foi aprovado ou negado")
        @Schema(description = "True para aprovar o desconto pendente, false para negar", example = "true")
        Boolean aprovado,

        @Size(max = 500, message = "A observação deve ter no máximo 500 caracteres")
        @Schema(description = "Observação ou justificativa da decisão (obrigatória ao negar)", nullable = true)
        String observacao,

        @Schema(description = "E-mail do gerente/autorizador caso o operador logado não tenha CAIXA_AUTORIZAR_DESCONTO", nullable = true)
        String autorizadorEmail,

        @Schema(description = "Senha do gerente/autorizador caso o operador logado não tenha CAIXA_AUTORIZAR_DESCONTO", nullable = true)
        String autorizadorSenha
) {
}
