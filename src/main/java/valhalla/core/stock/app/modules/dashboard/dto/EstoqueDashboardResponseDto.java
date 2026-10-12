package valhalla.core.stock.app.modules.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Indicadores e alertas de estoque da dashboard")
public record EstoqueDashboardResponseDto(
        long totalProdutos,
        long produtosComMinimoDefinido,
        long produtosSemMinimoDefinido,
        long criticos,
        long zerados,
        long saudaveis,
        int saudePercentual,
        List<AlertaEstoqueDashboardResponseDto> alertas
) {
}
