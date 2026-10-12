package valhalla.core.stock.app.modules.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Situação de um módulo ainda não integrado à dashboard")
public record ModuloDashboardResponseDto(
        DisponibilidadeModuloDashboard status
) {
}
