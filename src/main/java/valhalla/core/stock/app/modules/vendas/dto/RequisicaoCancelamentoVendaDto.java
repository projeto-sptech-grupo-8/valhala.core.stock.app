package valhalla.core.stock.app.modules.vendas.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequisicaoCancelamentoVendaDto(
        @NotBlank(message = "O motivo do cancelamento é obrigatório")
        @Size(max = 255, message = "O motivo deve ter no máximo 255 caracteres")
        @Schema(example = "Pedido lançado errado")
        String motivo
) {
}
