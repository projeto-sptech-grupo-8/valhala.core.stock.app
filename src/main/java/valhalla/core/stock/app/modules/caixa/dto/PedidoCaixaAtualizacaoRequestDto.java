package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import valhalla.core.stock.app.modules.caixa.entity.CanalPedidoCaixa;

import java.util.List;

public record PedidoCaixaAtualizacaoRequestDto(
        @Schema(description = "Novo canal de atendimento", example = "MESA", nullable = true)
        CanalPedidoCaixa canal,

        @Size(max = 60, message = "O identificador do atendimento deve ter no máximo 60 caracteres")
        @Schema(description = "Novo rótulo de identificação do pedido", example = "Mesa 04", nullable = true)
        String identificadorAtendimento,

        @Size(max = 500, message = "A observação deve ter no máximo 500 caracteres")
        @Schema(description = "Observação do pedido", nullable = true)
        String observacao,

        @Schema(description = "Lista atualizada de itens do pedido", nullable = true)
        List<@Valid ItemPedidoCaixaRequestDto> itens
) {
}
