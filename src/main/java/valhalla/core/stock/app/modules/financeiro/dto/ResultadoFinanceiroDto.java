package valhalla.core.stock.app.modules.financeiro.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Resultado (DRE gerencial) do período. Percentuais de 0 a 100, nulos sem receita.")
public record ResultadoFinanceiroDto(
        LocalDate inicio,
        LocalDate fim,
        @Schema(description = "Vendas concluídas no período, inclusive as canceladas depois.")
        BigDecimal receitaBruta,
        @Schema(description = "Vendas do período que foram canceladas.")
        BigDecimal cancelamentos,
        BigDecimal receitaLiquida,
        @Schema(description = "Custo das mercadorias vendidas, pelo custo gravado em cada venda.")
        BigDecimal cmv,
        BigDecimal lucroBruto,
        BigDecimal margemBrutaPercentual,
        @Schema(description = "Contas a pagar DESPESA_FIXA com vencimento no período (não canceladas).")
        BigDecimal despesasFixas,
        @Schema(description = "Contas a pagar DESPESA_VARIAVEL com vencimento no período (não canceladas).")
        BigDecimal despesasVariaveis,
        BigDecimal lucroLiquido,
        BigDecimal margemLiquidaPercentual,
        BigDecimal cmvPercentual,
        long vendas,
        List<VendaCategoria> vendasPorCategoria
) {

    public record VendaCategoria(String categoria, BigDecimal valor, BigDecimal participacaoPercentual) {
    }
}
