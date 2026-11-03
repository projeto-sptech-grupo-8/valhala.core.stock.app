package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.caixa.entity.TipoMovimentacaoCaixa;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MovimentacaoCaixaResponseDto(
        @Schema(description = "Identificador da movimentação") Long id,
        @Schema(description = "Tipo da movimentação") TipoMovimentacaoCaixa tipo,
        @Schema(description = "Valor movimentado") BigDecimal valor,
        @Schema(description = "Motivo informado") String motivo,
        @Schema(description = "Usuário responsável") UUID usuarioId,
        @Schema(description = "Nome do usuário responsável") String usuarioNome,
        @Schema(description = "Data e hora da movimentação") LocalDateTime ocorridoEm
) {
}
