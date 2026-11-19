package valhalla.core.stock.app.modules.caixa.fiscal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemNotaFiscalResponseDto(
        @Schema(description = "Identificador do item da nota fiscal") Long id,
        @Schema(description = "Identificador do produto no catálogo", nullable = true) UUID produtoId,
        @Schema(description = "Identificador do item no pedido de origem", nullable = true) Long itemPedidoId,
        @Schema(description = "Descrição do produto congelada na emissão") String descricaoProduto,
        @Schema(description = "Unidade comercial do item") String unidadeMedida,
        @Schema(description = "CFOP aplicado automaticamente") String cfop,
        @Schema(description = "NCM aplicado automaticamente") String ncm,
        @Schema(description = "CSOSN do Simples Nacional aplicado automaticamente") String csosn,
        @Schema(description = "Quantidade comercializada") BigDecimal quantidade,
        @Schema(description = "Preço unitário") BigDecimal precoUnitario,
        @Schema(description = "Valor bruto do item (quantidade × preço unitário)") BigDecimal valorBruto,
        @Schema(description = "Parcela do desconto rateada neste item") BigDecimal valorDesconto,
        @Schema(description = "Valor líquido total do item") BigDecimal valorTotal
) {
}
