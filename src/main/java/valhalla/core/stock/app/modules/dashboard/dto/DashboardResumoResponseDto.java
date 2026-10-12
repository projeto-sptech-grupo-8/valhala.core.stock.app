package valhalla.core.stock.app.modules.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resumo consolidado da dashboard do estabelecimento autenticado")
public record DashboardResumoResponseDto(
        ReferenciaDashboardResponseDto referencia,
        EstoqueDashboardResponseDto estoque,
        MovimentacoesDashboardResponseDto movimentacoes,
        ModuloDashboardResponseDto financeiro,
        ModuloDashboardResponseDto caixa
) {
}
