package valhalla.core.stock.app.modules.caixa.pedido.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemPedidoCaixaResponseDto(
        @Schema(description = "Identificador do item no pedido") Long id,
        @Schema(description = "Identificador do produto") UUID produtoId,
        @Schema(description = "Descrição congelada do produto no momento da venda") String descricaoProduto,
        @Schema(description = "Quantidade vendida") BigDecimal quantidade,
        @Schema(description = "Preço unitário congelado") BigDecimal precoUnitario,
        @Schema(description = "Custo unitário congelado") BigDecimal custoUnitario,
        @Schema(description = "Desconto aplicado ao item") BigDecimal desconto,
        @Schema(description = "Subtotal do item") BigDecimal subtotal,
        @Schema(description = "Identificador da categoria do produto", nullable = true) Integer categoriaId,
        @Schema(description = "Snapshot do nome da categoria", nullable = true) String categoriaNome
) {
}
