package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record TerminalCaixaAtualizacaoRequestDto(
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        @Schema(description = "Novo nome do terminal; omitido mantém o atual", example = "Maquininha 2",
                nullable = true)
        String nome,

        @Schema(description = "Ativa ou inativa o terminal; omitido mantém o atual", example = "true",
                nullable = true)
        Boolean ativo
) {
}
