package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.caixa.entity.CanalPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.entity.StatusDescontoPedido;
import valhalla.core.stock.app.modules.caixa.entity.StatusPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.entity.TipoDescontoPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PedidoCaixaDetalheResponseDto(
        @Schema(description = "Identificador do pedido") UUID id,
        @Schema(description = "Número sequencial do pedido no estabelecimento") Long numero,
        @Schema(description = "Sessão de caixa vinculada", nullable = true) UUID sessaoCaixaId,
        @Schema(description = "Operador que abriu o pedido") UUID operadorUsuarioId,
        @Schema(description = "Nome do operador") String operadorNome,
        @Schema(description = "Status atual do pedido") StatusPedidoCaixa status,
        @Schema(description = "Canal do pedido") CanalPedidoCaixa canal,
        @Schema(description = "Rótulo de atendimento (ex.: Mesa 04)", nullable = true) String identificadorAtendimento,
        @Schema(description = "Observação do pedido", nullable = true) String observacao,
        @Schema(description = "Subtotal dos itens") BigDecimal subtotal,
        @Schema(description = "Desconto efetivamente aprovado e aplicado") BigDecimal desconto,
        @Schema(description = "Total líquido do pedido") BigDecimal total,
        @Schema(description = "Total já pago com status APROVADO") BigDecimal totalPago,
        @Schema(description = "Saldo restante para concluir o pedido") BigDecimal saldoRestante,
        @Schema(description = "Status do fluxo de desconto") StatusDescontoPedido statusDesconto,
        @Schema(description = "Tipo do desconto solicitado/aplicado", nullable = true) TipoDescontoPedido tipoDesconto,
        @Schema(description = "Valor informado para o desconto (% ou R$)", nullable = true) BigDecimal valorDescontoInformado,
        @Schema(description = "Valor em R$ do desconto solicitado", nullable = true) BigDecimal descontoSolicitado,
        @Schema(description = "Motivo do desconto", nullable = true) String motivoDesconto,
        @Schema(description = "Usuário que aprovou/negou o desconto", nullable = true) UUID descontoDecididoPorUsuarioId,
        @Schema(description = "Nome de quem aprovou/negou o desconto", nullable = true) String descontoDecididoPorNome,
        @Schema(description = "Data/hora da decisão sobre o desconto", nullable = true) LocalDateTime descontoDecididoEm,
        @Schema(description = "Observação da decisão sobre o desconto", nullable = true) String observacaoDecisaoDesconto,
        @Schema(description = "Motivo do cancelamento", nullable = true) String motivoCancelamento,
        @Schema(description = "Usuário que cancelou o pedido", nullable = true) UUID canceladoPorUsuarioId,
        @Schema(description = "Motivo do estorno", nullable = true) String motivoEstorno,
        @Schema(description = "Usuário que estornou o pedido", nullable = true) UUID estornadoPorUsuarioId,
        @Schema(description = "Data/hora de criação") LocalDateTime criadoEm,
        @Schema(description = "Data/hora da última atualização") LocalDateTime atualizadoEm,
        @Schema(description = "Data/hora de conclusão", nullable = true) LocalDateTime concluidoEm,
        @Schema(description = "Data/hora de cancelamento", nullable = true) LocalDateTime canceladoEm,
        @Schema(description = "Data/hora de estorno", nullable = true) LocalDateTime estornadoEm,
        @Schema(description = "Itens do pedido") List<ItemPedidoCaixaResponseDto> itens,
        @Schema(description = "Pagamentos e tentativas registradas para o pedido") List<PagamentoPedidoResponseDto> pagamentos
) {
}
