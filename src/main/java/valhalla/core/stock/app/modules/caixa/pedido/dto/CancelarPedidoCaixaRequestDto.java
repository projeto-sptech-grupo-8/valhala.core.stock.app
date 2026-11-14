package valhalla.core.stock.app.modules.caixa.pedido.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record CancelarPedidoCaixaRequestDto(
        @Size(max = 500, message = "O motivo do cancelamento deve ter no máximo 500 caracteres")
        @Schema(description = "Motivo do cancelamento (obrigatório quando o pedido já possui itens)", nullable = true)
        String motivo,

        @Schema(description = "E-mail do autorizador caso o usuário não possua CAIXA_CANCELAR", nullable = true)
        String autorizadorEmail,

        @Schema(description = "Senha do autorizador caso o usuário não possua CAIXA_CANCELAR", nullable = true)
        String autorizadorSenha
) {
}
