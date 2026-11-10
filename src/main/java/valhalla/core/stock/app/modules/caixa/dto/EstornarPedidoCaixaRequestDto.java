package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EstornarPedidoCaixaRequestDto(
        @NotBlank(message = "O motivo do estorno é obrigatório")
        @Size(max = 500, message = "O motivo do estorno deve ter no máximo 500 caracteres")
        @Schema(description = "Motivo do estorno total do pedido concluído", example = "Cobrança duplicada / desistência do cliente")
        String motivo,

        @Schema(description = "E-mail do autorizador caso o usuário logado não possua CAIXA_ESTORNAR", nullable = true)
        String autorizadorEmail,

        @Schema(description = "Senha do autorizador caso o usuário logado não possua CAIXA_ESTORNAR", nullable = true)
        String autorizadorSenha
) {
}
