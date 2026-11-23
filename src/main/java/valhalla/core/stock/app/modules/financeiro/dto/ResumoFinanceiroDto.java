package valhalla.core.stock.app.modules.financeiro.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Visão geral financeira na data de referência (hoje, no fuso da operação).")
public record ResumoFinanceiroDto(
        LocalDate referencia,
        @Schema(description = "Recebido das vendas + contas recebidas − contas pagas, desde o início dos registros.")
        BigDecimal saldoRealizado,
        @Schema(description = "Contas a receber em aberto que vencem nos próximos 30 dias (inclui as vencidas).")
        ContasEmAberto aReceber30Dias,
        @Schema(description = "Contas a pagar em aberto que vencem nos próximos 30 dias (inclui as vencidas).")
        ContasEmAberto aPagar30Dias,
        @Schema(description = "Saldo realizado ÷ a pagar em 30 dias. Nulo sem contas a pagar.")
        BigDecimal coberturaContasAPagar,
        Faturamento faturamento,
        @Schema(description = "Fluxo de caixa realizado dos últimos 6 meses, do mais antigo ao atual.")
        List<FluxoMensal> fluxoCaixa
) {

    public record ContasEmAberto(BigDecimal valor, long quantidade, long vencidas, long vencemEmAte7Dias) {
    }

    @Schema(description = "Mês corrente até hoje, comparado ao mesmo intervalo de dias do mês anterior.")
    public record Faturamento(
            BigDecimal mesAtual,
            BigDecimal mesAnteriorMesmoPeriodo,
            @Schema(description = "Variação percentual; nula sem faturamento no mês anterior.")
            BigDecimal crescimentoPercentual,
            BigDecimal cmvMesAtual,
            @Schema(description = "CMV ÷ faturamento do mês, de 0 a 100.")
            BigDecimal cmvPercentual,
            long vendasMesAtual
    ) {
    }

    @Schema(description = "mes no formato yyyy-MM. Receitas: vendas e contas recebidas; despesas: contas pagas.")
    public record FluxoMensal(String mes, BigDecimal receitas, BigDecimal despesas, BigDecimal saldo) {
    }
}
