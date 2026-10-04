package valhalla.core.stock.app.modules.estoque.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record CategoriaRequestDto(
        @NotBlank(message = "O nome da categoria é obrigatório")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        @Schema(description = "Nome único da categoria no estabelecimento", example = "Bebidas")
        String nome,

        @Schema(description = "Descrição opcional da categoria", example = "Bebidas alcoólicas e não alcoólicas")
        String descricao
) {}
