package valhalla.core.stock.app.modules.estoque.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Receita completa de um drink. A atualização substitui todos os ingredientes atuais.")
public record ReceitaDrinkRequestDto(
        @NotEmpty(message = "Um drink deve possuir ao menos um ingrediente")
        List<@Valid IngredienteDrinkRequestDto> ingredientes
) {
}
