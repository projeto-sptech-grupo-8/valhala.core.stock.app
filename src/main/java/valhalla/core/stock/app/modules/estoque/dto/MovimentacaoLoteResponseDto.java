package valhalla.core.stock.app.modules.estoque.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Parte da movimentação que entrou em (ou saiu de) um lote. Quantidade na unidade-base do estoque.")
public record MovimentacaoLoteResponseDto(
        Long loteId,
        String codigo,
        LocalDate validade,
        BigDecimal quantidade
) {
}
