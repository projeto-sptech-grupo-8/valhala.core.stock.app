package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemPedidoCaixaRequestDto(
        @NotNull(message = "O produto é obrigatório")
        @Schema(description = "Identificador do produto ou drink")
        UUID produtoId,

        @NotNull(message = "A quantidade é obrigatória")
        @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
        @Digits(integer = 9, fraction = 3, message = "A quantidade deve ter no máximo 3 casas decimais")
        @Schema(description = "Quantidade solicitada do item", example = "2")
        BigDecimal quantidade
) {
}
