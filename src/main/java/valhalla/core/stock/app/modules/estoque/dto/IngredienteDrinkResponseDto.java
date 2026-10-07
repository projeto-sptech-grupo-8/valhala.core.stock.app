package valhalla.core.stock.app.modules.estoque.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Ingrediente da receita de um drink")
public record IngredienteDrinkResponseDto(
        UUID produtoId,
        String produtoNome,
        BigDecimal quantidade,
        UnidadeConsumoDrink unidadeConsumo
) {
}
