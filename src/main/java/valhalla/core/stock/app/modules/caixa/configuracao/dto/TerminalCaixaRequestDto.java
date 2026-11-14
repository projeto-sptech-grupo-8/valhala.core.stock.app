package valhalla.core.stock.app.modules.caixa.configuracao.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TerminalCaixaRequestDto(
        @NotBlank(message = "O identificador do terminal é obrigatório")
        @Size(max = 100, message = "O identificador do terminal deve ter no máximo 100 caracteres")
        @Schema(description = "Identificador do dispositivo no Mercado Pago; único em todo o sistema",
                example = "PAX_A910__SMARTPOS1234567890")
        String terminalId,

        @NotBlank(message = "O nome do terminal é obrigatório")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
        @Schema(description = "Nome amigável, único dentro do caixa", example = "Maquininha 1")
        String nome
) {
}
