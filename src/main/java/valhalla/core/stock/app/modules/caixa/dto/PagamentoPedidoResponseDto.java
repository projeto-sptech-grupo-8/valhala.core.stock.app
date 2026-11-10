package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.caixa.entity.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.entity.PoliticaJurosCaixa;
import valhalla.core.stock.app.modules.caixa.entity.StatusPagamentoPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PagamentoPedidoResponseDto(
        @Schema(description = "Identificador do pagamento") UUID id,
        @Schema(description = "Identificador do pedido") UUID pedidoId,
        @Schema(description = "Sessão de caixa que recebeu o pagamento", nullable = true) UUID sessaoCaixaId,
        @Schema(description = "Forma de pagamento") FormaPagamentoCaixa forma,
        @Schema(description = "Status do pagamento") StatusPagamentoPedido status,
        @Schema(description = "Provedor do processamento: INTERNO, MANUAL ou MERCADO_PAGO") String provedor,
        @Schema(description = "Valor abatido do saldo do pedido") BigDecimal valor,
        @Schema(description = "Valor cobrado do cliente (pode incluir repasse de taxa)", nullable = true) BigDecimal valorCobrado,
        @Schema(description = "Valor físico recebido em dinheiro", nullable = true) BigDecimal valorRecebido,
        @Schema(description = "Troco devolvido em dinheiro") BigDecimal valorTroco,
        @Schema(description = "Valor estornado") BigDecimal valorEstornado,
        @Schema(description = "Quantidade de parcelas") Integer parcelas,
        @Schema(description = "Política de juros/taxa aplicada", nullable = true) PoliticaJurosCaixa politicaJuros,
        @Schema(description = "Taxa percentual aplicada na venda", nullable = true) BigDecimal taxaPercentualAplicada,
        @Schema(description = "Taxa fixa aplicada na venda", nullable = true) BigDecimal taxaFixaAplicada,
        @Schema(description = "Valor estimado da taxa da adquirente", nullable = true) BigDecimal valorTaxaEstimada,
        @Schema(description = "Valor líquido estimado para o estabelecimento", nullable = true) BigDecimal valorLiquidoEstimado,
        @Schema(description = "Valor de juros/acréscimo repassado ao cliente", nullable = true) BigDecimal valorJuros,
        @Schema(description = "Chave de idempotência informada", nullable = true) String chaveIdempotencia,
        @Schema(description = "Referência do comprovante/NSU", nullable = true) String comprovanteReferencia,
        @Schema(description = "Motivo de cancelamento do pagamento", nullable = true) String motivoCancelamento,
        @Schema(description = "Motivo de estorno do pagamento", nullable = true) String motivoEstorno,
        @Schema(description = "Data/hora em que o pagamento foi solicitado", nullable = true) LocalDateTime solicitadoEm,
        @Schema(description = "Data/hora da aprovação", nullable = true) LocalDateTime pagoEm,
        @Schema(description = "Data/hora do cancelamento", nullable = true) LocalDateTime canceladoEm,
        @Schema(description = "Data/hora do estorno", nullable = true) LocalDateTime estornadoEm,
        @Schema(description = "Data/hora de criação do registro") LocalDateTime criadoEm
) {
}
