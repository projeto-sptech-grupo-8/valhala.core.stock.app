package valhalla.core.stock.app.modules.caixa.configuracao.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CaixaRequestDto(
        @NotBlank(message = "O nome do caixa é obrigatório")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        @Schema(description = "Nome único do caixa no estabelecimento", example = "Caixa 1")
        String nome
) {
}
