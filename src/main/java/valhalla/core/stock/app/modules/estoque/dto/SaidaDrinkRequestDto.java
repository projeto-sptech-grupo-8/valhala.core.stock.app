package valhalla.core.stock.app.modules.estoque.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Baixa dos ingredientes necessária para servir drinks. O drink não possui estoque próprio.")
public record SaidaDrinkRequestDto(
        @NotNull(message = "O drink é obrigatório")
        UUID drinkId,

        @NotNull(message = "A quantidade de drinks é obrigatória")
        @Positive(message = "A quantidade de drinks deve ser maior que zero")
        BigDecimal quantidade,

        String motivo
) {
}
