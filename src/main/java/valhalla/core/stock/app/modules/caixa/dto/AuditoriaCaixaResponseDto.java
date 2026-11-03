package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.util.RawValue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AuditoriaCaixaResponseDto(
        @Schema(description = "Identificador do evento") Long id,
        @Schema(description = "Código do evento", example = "FECHAMENTO_CAIXA") String tipoEvento,
        @Schema(description = "Entidade afetada", example = "SESSAO_CAIXA") String entidade,
        @Schema(description = "Identificador da entidade afetada") String entidadeId,
        @Schema(description = "Sessão de caixa relacionada", nullable = true) UUID sessaoCaixaId,
        @Schema(description = "Usuário responsável", nullable = true) UUID usuarioId,
        @Schema(description = "Snapshot do nome do usuário", nullable = true) String usuarioNome,
        @Schema(description = "Descrição do evento", nullable = true) String descricao,
        @Schema(description = "Valor financeiro do evento", nullable = true) BigDecimal valor,
        @Schema(description = "Motivo informado", nullable = true) String motivo,
        @Schema(description = "Usuário que autorizou", nullable = true) UUID autorizadoPorUsuarioId,
        @Schema(description = "Snapshot do nome do autorizador", nullable = true) String autorizadoPorNome,
        @Schema(description = "Pedido relacionado", nullable = true) UUID pedidoId,
        @Schema(description = "Rótulo do pedido", nullable = true) String pedidoIdentificador,
        @Schema(description = "Pagamento relacionado", nullable = true) UUID pagamentoPedidoId,
        @Schema(description = "Dados anteriores ao evento", nullable = true) RawValue dadosAnteriores,
        @Schema(description = "Dados posteriores ao evento", nullable = true) RawValue dadosNovos,
        @Schema(description = "Data e hora do evento") LocalDateTime ocorridoEm
) {
}
