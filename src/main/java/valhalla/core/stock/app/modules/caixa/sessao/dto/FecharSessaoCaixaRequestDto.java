package valhalla.core.stock.app.modules.caixa.sessao.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FecharSessaoCaixaRequestDto(

        @NotNull(message = "O dinheiro contado é obrigatório")
        @DecimalMin(value = "0.00", message = "O dinheiro contado não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "O dinheiro contado deve ter no máximo 2 casas decimais")
        @Schema(description = "Valor total em dinheiro conferido na gaveta", example = "450.00")
        BigDecimal dinheiroContado,

        @Size(max = 2000, message = "A justificativa deve ter no máximo 2000 caracteres")
        @Schema(description = "Justificativa obrigatória quando existe diferença", example = "Faltou troco para um cliente",
                nullable = true)
        String justificativaDivergencia,

        @Size(max = 2000, message = "A observação deve ter no máximo 2000 caracteres")
        @Schema(description = "Observação opcional do fechamento", example = "Turno encerrado sem intercorrências",
                nullable = true)
        String observacao
) {
}
