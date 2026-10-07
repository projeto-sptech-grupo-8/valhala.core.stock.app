package valhalla.core.stock.app.modules.estoque.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Ingrediente rastreável da receita de um drink")
public record IngredienteDrinkRequestDto(
        @NotNull(message = "O produto ingrediente é obrigatório")
        UUID produtoId,

        @NotNull(message = "A quantidade do ingrediente é obrigatória")
        @Positive(message = "A quantidade do ingrediente deve ser maior que zero")
        @Schema(example = "50")
        BigDecimal quantidade,

        @NotNull(message = "A unidade de consumo é obrigatória")
        @Schema(description = "ML para ingrediente fracionado; UN para ingrediente unitário", example = "ML")
        UnidadeConsumoDrink unidadeConsumo
) {
}
