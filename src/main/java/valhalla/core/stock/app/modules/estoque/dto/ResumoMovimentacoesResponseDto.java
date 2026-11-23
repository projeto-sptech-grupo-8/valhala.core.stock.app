package valhalla.core.stock.app.modules.estoque.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Indicadores das movimentações do período, contados no banco (não só a página carregada).")
public record ResumoMovimentacoesResponseDto(
        @Schema(description = "Início do período (inclusivo); nulo quando é o histórico completo.")
        LocalDate desde,
        long total,
        @Schema(description = "ENTRADA_INICIAL e ENTRADA.")
        long entradas,
        @Schema(description = "SAIDA, SAIDA_DRINK e PERDA.")
        long saidas,
        long perdas,
        @Schema(description = "AJUSTE_POSITIVO e AJUSTE_NEGATIVO lançados manualmente (sem os estornos de venda).")
        long ajustes,
        @Schema(description = "Ajustes automáticos que devolveram ao estoque itens de vendas canceladas.")
        long estornosVenda,
        @Schema(description = "Perdas ÷ saídas, de 0 a 100.")
        BigDecimal taxaPerdasPercentual,
        @Schema(description = "Ajustes ÷ total, de 0 a 100.")
        BigDecimal taxaAjustesPercentual
) {
}
