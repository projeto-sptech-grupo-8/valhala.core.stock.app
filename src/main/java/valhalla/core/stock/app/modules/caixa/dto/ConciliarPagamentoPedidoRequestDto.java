package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConciliarPagamentoPedidoRequestDto(
        @NotNull(message = "Informe se o pagamento foi aprovado na conciliação")
        @Schema(description = "True para aprovar o pagamento pendente de conciliação, false para cancelar/rejeitar", example = "true")
        Boolean aprovado,

        @Size(max = 500, message = "O motivo deve ter no máximo 500 caracteres")
        @Schema(description = "Observação da conciliação ou motivo do cancelamento (obrigatório quando aprovado = false)", nullable = true)
        String motivo,

        @Size(max = 255, message = "A referência do comprovante deve ter no máximo 255 caracteres")
        @Schema(description = "NSU ou referência atualizada do comprovante", nullable = true)
        String comprovanteReferencia
) {
}
