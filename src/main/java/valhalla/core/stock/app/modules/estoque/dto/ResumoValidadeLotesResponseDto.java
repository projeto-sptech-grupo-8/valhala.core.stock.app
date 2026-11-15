package valhalla.core.stock.app.modules.estoque.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Indicadores de validade dos lotes com saldo, de produtos ativos do estabelecimento.")
public record ResumoValidadeLotesResponseDto(
        LocalDate referencia,
        long lotesComSaldo,
        long vencidos,
        @Schema(description = "Vencem de hoje até 7 dias.")
        long vencemEm7Dias,
        @Schema(description = "Vencem de 8 a 30 dias.")
        long vencemEm30Dias,
        long semValidade,
        @Schema(description = "Valor a custo dos lotes vencidos.")
        BigDecimal valorVencido,
        @Schema(description = "Valor a custo dos lotes vencidos ou que vencem em até 30 dias.")
        BigDecimal valorEmRisco
) {
}
