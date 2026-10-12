package valhalla.core.stock.app.modules.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Produto com saldo abaixo do estoque mínimo")
public record AlertaEstoqueDashboardResponseDto(
        UUID produtoId,
        String nome,
        String categoria,
        String unidadeMedida,
        BigDecimal quantidadeAtual,
        BigDecimal estoqueMinimo,
        StatusEstoqueDashboard status
) {
}
