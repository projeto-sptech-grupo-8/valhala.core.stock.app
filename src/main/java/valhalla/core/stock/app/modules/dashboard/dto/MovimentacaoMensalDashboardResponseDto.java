package valhalla.core.stock.app.modules.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Quantidade de registros de entrada e saída em um mês")
public record MovimentacaoMensalDashboardResponseDto(
        String mes,
        long entradas,
        long saidas
) {
}
