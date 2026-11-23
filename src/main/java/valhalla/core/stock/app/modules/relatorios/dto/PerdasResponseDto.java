package valhalla.core.stock.app.modules.relatorios.dto;

import java.util.List;

/** Perdas a custo; granularidade: DIA (até 7 dias), SEMANA (até 30) ou MES (acima disso). */
public record PerdasResponseDto(
        RelatorioResponseDto.Referencia referencia,
        RelatorioResponseDto.Totais totais,
        String granularidade,
        List<RelatorioLinhas.Perda> itens
) {
}
