package valhalla.core.stock.app.modules.caixa.pedido.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.NotaFiscalPedidoVinculadaDto;
import valhalla.core.stock.app.modules.caixa.pedido.enums.CanalPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusDescontoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.enums.TipoDescontoPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PedidoCaixaResumoResponseDto(
        @Schema(description = "Identificador do pedido") UUID id,
        @Schema(description = "Número sequencial do pedido no estabelecimento") Long numero,
        @Schema(description = "Sessão de caixa vinculada", nullable = true) UUID sessaoCaixaId,
        @Schema(description = "Operador que abriu o pedido") UUID operadorUsuarioId,
        @Schema(description = "Nome do operador") String operadorNome,
        @Schema(description = "Status atual do pedido") StatusPedidoCaixa status,
        @Schema(description = "Canal do pedido") CanalPedidoCaixa canal,
        @Schema(description = "Rótulo de atendimento (ex.: Mesa 04)", nullable = true) String identificadorAtendimento,
        @Schema(description = "Subtotal dos itens") BigDecimal subtotal,
        @Schema(description = "Desconto efetivamente aprovado e aplicado") BigDecimal desconto,
        @Schema(description = "Total líquido do pedido") BigDecimal total,
        @Schema(description = "Total já pago com status APROVADO") BigDecimal totalPago,
        @Schema(description = "Saldo restante para concluir o pedido") BigDecimal saldoRestante,
        @Schema(description = "Status do fluxo de desconto") StatusDescontoPedido statusDesconto,
        @Schema(description = "Tipo do desconto solicitado/aplicado", nullable = true) TipoDescontoPedido tipoDesconto,
        @Schema(description = "Valor informado para o desconto (% ou R$)", nullable = true) BigDecimal valorDescontoInformado,
        @Schema(description = "Valor em R$ do desconto solicitado aguardando aprovação", nullable = true) BigDecimal descontoSolicitado,
        @Schema(description = "Quantidade de linhas de itens no pedido") int quantidadeItens,
        @Schema(description = "Data/hora de criação") LocalDateTime criadoEm,
        @Schema(description = "Data/hora da última atualização") LocalDateTime atualizadoEm,
        @Schema(description = "Data/hora de conclusão", nullable = true) LocalDateTime concluidoEm,
        @Schema(description = "Data/hora de cancelamento", nullable = true) LocalDateTime canceladoEm,
        @Schema(description = "Data/hora de estorno", nullable = true) LocalDateTime estornadoEm,
        @Schema(description = "Última NFC-e vinculada ao pedido (se houver)", nullable = true) NotaFiscalPedidoVinculadaDto notaFiscal
) {
}
