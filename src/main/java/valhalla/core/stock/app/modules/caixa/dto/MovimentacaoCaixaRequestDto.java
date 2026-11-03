package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import valhalla.core.stock.app.modules.caixa.entity.TipoMovimentacaoCaixa;

import java.math.BigDecimal;

public record MovimentacaoCaixaRequestDto(

        @NotNull(message = "O tipo da movimentação é obrigatório")
        @Schema(description = "Tipo da movimentação", example = "SANGRIA")
        TipoMovimentacaoCaixa tipo,

        @NotNull(message = "O valor é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "O valor deve ter no máximo 2 casas decimais")
        @Schema(description = "Valor movimentado", example = "50.00")
        BigDecimal valor,

        @NotBlank(message = "O motivo é obrigatório")
        @Size(max = 1000, message = "O motivo deve ter no máximo 1000 caracteres")
        @Schema(description = "Motivo da sangria ou do reforço", example = "Depósito bancário")
        String motivo
) {
}
