package valhalla.core.stock.app.modules.estoque.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MovimentacaoCommand(
        TipoMovimentacaoEstoque tipo,
        BigDecimal quantidade,
        String motivo,
        String lote,
        String numeroNotaFiscal,
        UUID origemDrinkId,
        JsonNode receitaAplicada,
        LocalDate validade,
        Long loteId
) {
    public MovimentacaoCommand(
            TipoMovimentacaoEstoque tipo,
            BigDecimal quantidade,
            String motivo,
            String lote,
            String numeroNotaFiscal,
            UUID origemDrinkId,
            JsonNode receitaAplicada
    ) {
        this(tipo, quantidade, motivo, lote, numeroNotaFiscal, origemDrinkId, receitaAplicada, null, null);
    }
}
