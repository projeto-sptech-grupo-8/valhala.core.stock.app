package valhalla.core.stock.app.modules.estoque.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record CategoriaAtualizacaoRequestDto(

        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        @Schema(description = "Novo nome da categoria", example = "Bebidas geladas")
        String nome,

        @Schema(description = "Nova descrição. Envie uma string vazia para removê-la.",
                example = "Produtos servidos gelados")
        String descricao,

        @Schema(description = "Define se a categoria está disponível para uso", example = "true")
        Boolean ativo
) {
}
