package valhalla.core.stock.app.modules.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "Data de referência e período usado para compor a dashboard")
public record ReferenciaDashboardResponseDto(
        LocalDateTime geradoEm,
        LocalDate inicioPeriodoMovimentacoes,
        LocalDate fimPeriodoMovimentacoes,
        String fusoHorario
) {
}
