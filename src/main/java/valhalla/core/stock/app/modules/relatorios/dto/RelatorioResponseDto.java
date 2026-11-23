package valhalla.core.stock.app.modules.relatorios.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Envelope comum dos relatórios: período consultado, totais de vendas do período e as linhas do relatório. */
public record RelatorioResponseDto<T>(
        Referencia referencia,
        Totais totais,
        List<T> itens
) {
    public record Referencia(int dias, LocalDate de, LocalDate ate, String fusoHorario) {
    }

    public record Totais(long vendas, BigDecimal faturamento) {
    }
}
