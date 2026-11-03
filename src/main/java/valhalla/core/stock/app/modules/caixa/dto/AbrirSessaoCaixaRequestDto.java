package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AbrirSessaoCaixaRequestDto(

        @NotNull(message = "O fundo de troco é obrigatório")
        @DecimalMin(value = "0.00", message = "O fundo de troco não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "O fundo de troco deve ter no máximo 2 casas decimais")
        @Schema(description = "Dinheiro inicial disponível para troco", example = "100.00")
        BigDecimal fundoTroco,

        @Size(max = 2000, message = "A observação deve ter no máximo 2000 caracteres")
        @Schema(description = "Observação opcional da abertura", example = "Abertura do turno da noite",
                nullable = true)
        String observacao
) {
}
