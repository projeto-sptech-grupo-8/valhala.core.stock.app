package valhalla.core.stock.app.modules.estoque.dto;

import tools.jackson.databind.util.RawValue;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MovimentacaoResponseDto(
        Long id,
        UUID usuarioId,
        UUID produtoId,
        String produtoNome,
        String unidadeEstoque,
        TipoMovimentacaoEstoque tipo,
        BigDecimal quantidade,
        BigDecimal saldoAnterior,
        BigDecimal saldoPosterior,
        String motivo,
        String lote,
        String numeroNotaFiscal,
        UUID origemDrinkId,
        RawValue receitaAplicada,
        LocalDateTime ocorridoEm
) {
}
