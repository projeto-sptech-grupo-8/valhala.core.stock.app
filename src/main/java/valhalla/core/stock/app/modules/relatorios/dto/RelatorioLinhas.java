package valhalla.core.stock.app.modules.relatorios.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Linhas de cada relatório. Percentuais vão de 0 a 100. */
public final class RelatorioLinhas {

    private RelatorioLinhas() {
    }

    public record CurvaAbc(
            UUID produtoId, String nome, String categoria, BigDecimal quantidade, BigDecimal receita,
            int posicao, BigDecimal percentual, BigDecimal acumulado, String classe
    ) {
    }

    public record MargemCategoria(
            String categoria, BigDecimal quantidade, BigDecimal receita, BigDecimal cmv,
            BigDecimal lucro, BigDecimal margem, BigDecimal participacao
    ) {
    }

    public record Ruptura(
            UUID produtoId, String nome, String categoria, int dias, int ocorrencias,
            BigDecimal unidadesPerdidas, BigDecimal vendaPerdida, BigDecimal percentualDias
    ) {
    }

    /** diaSemana: 0 = segunda … 6 = domingo. Só células com venda. */
    public record Horario(int diaSemana, int hora, long vendas, BigDecimal receita) {
    }

    public record Canal(
            String canal, long vendas, BigDecimal faturamento, BigDecimal ticketMedio,
            BigDecimal participacao, BigDecimal itensPorVenda
    ) {
    }

    /** periodo: primeiro dia do dia/semana/mês, conforme a granularidade da resposta. */
    public record Perda(LocalDate periodo, String motivo, BigDecimal valor) {
    }
}
