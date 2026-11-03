package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record ResolverDivergenciaCaixaRequestDto(

        @Size(max = 2000, message = "A observação deve ter no máximo 2000 caracteres")
        @Schema(description = "Observação da aprovação gerencial", example = "Divergência conferida e aprovada",
                nullable = true)
        String observacao
) {
}
