package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import valhalla.core.stock.app.modules.caixa.entity.CanalPedidoCaixa;

import java.util.List;
import java.util.UUID;

public record PedidoCaixaRequestDto(
        @Schema(description = "Sessão de caixa vinculada; quando omitido, usa a sessão aberta do operador", nullable = true)
        UUID sessaoCaixaId,

        @Schema(description = "Canal de atendimento (padrão BALCAO)", example = "MESA", nullable = true)
        CanalPedidoCaixa canal,

        @Size(max = 60, message = "O identificador do atendimento deve ter no máximo 60 caracteres")
        @Schema(description = "Rótulo de identificação para pedidos simultâneos (obrigatório quando canal = MESA)", example = "Mesa 04", nullable = true)
        String identificadorAtendimento,

        @Size(max = 500, message = "A observação deve ter no máximo 500 caracteres")
        @Schema(description = "Observação opcional do pedido", nullable = true)
        String observacao,

        @Schema(description = "Lista inicial de itens do pedido", nullable = true)
        List<@Valid ItemPedidoCaixaRequestDto> itens
) {
}
