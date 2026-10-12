package valhalla.core.stock.app.modules.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Movimentação recente exibida na dashboard")
public record UltimaMovimentacaoDashboardResponseDto(
        Long id,
        LocalDateTime ocorridoEm,
        UUID produtoId,
        String produtoNome,
        String unidadeMedida,
        TipoMovimentacaoEstoque tipo,
        DirecaoMovimentacaoDashboard direcao,
        BigDecimal quantidade,
        String operador,
        String motivo
) {
}
