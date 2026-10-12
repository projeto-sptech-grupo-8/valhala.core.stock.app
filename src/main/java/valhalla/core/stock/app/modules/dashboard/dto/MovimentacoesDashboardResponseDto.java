package valhalla.core.stock.app.modules.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Série mensal e últimas movimentações de estoque")
public record MovimentacoesDashboardResponseDto(
        List<MovimentacaoMensalDashboardResponseDto> serieMensal,
        List<UltimaMovimentacaoDashboardResponseDto> ultimas
) {
}
