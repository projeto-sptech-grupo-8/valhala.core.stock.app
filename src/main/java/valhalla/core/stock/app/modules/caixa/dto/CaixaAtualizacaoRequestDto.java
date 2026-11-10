package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record CaixaAtualizacaoRequestDto(
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        @Schema(description = "Novo nome do caixa; omitido mantém o atual", example = "Caixa 2",
                nullable = true)
        String nome,

        @Schema(description = "Ativa ou inativa o caixa; omitido mantém o atual", example = "true",
                nullable = true)
        Boolean ativo
) {
}
