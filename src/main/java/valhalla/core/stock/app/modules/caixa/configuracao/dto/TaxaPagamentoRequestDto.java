package valhalla.core.stock.app.modules.caixa.configuracao.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;

import java.math.BigDecimal;

public record TaxaPagamentoRequestDto(
        @NotNull(message = "forma é obrigatória")
        @Schema(description = "Forma de pagamento com taxa: DEBITO, CREDITO ou PIX", example = "CREDITO")
        FormaPagamentoCaixa forma,

        @NotNull(message = "parcelas é obrigatório")
        @Min(value = 1, message = "parcelas deve ser no mínimo 1")
        @Max(value = 12, message = "parcelas deve ser no máximo 12")
        @Schema(description = "Número de parcelas; débito e Pix aceitam somente 1", example = "3",
                minimum = "1", maximum = "12")
        Integer parcelas,

        @NotNull(message = "taxaPercentual é obrigatória")
        @DecimalMin(value = "0.0000", message = "A taxa percentual não pode ser negativa")
        @DecimalMax(value = "100.0000", message = "A taxa percentual não pode ser maior que 100")
        @Digits(integer = 3, fraction = 4, message = "A taxa percentual deve ter no máximo 4 casas decimais")
        @Schema(description = "Percentual total sobre o valor da venda", example = "11.5000")
        BigDecimal taxaPercentual,

        @NotNull(message = "taxaFixa é obrigatória")
        @DecimalMin(value = "0.00", message = "A taxa fixa não pode ser negativa")
        @Digits(integer = 10, fraction = 2, message = "A taxa fixa deve ter no máximo 2 casas decimais")
        @Schema(description = "Valor fixo por transação; zero quando não houver", example = "0.00")
        BigDecimal taxaFixa,

        @Schema(description = "Repassa a taxa ao cliente; nulo segue a política de juros da configuração",
                example = "false", nullable = true)
        Boolean repassarAoCliente,

        @NotNull(message = "ativo é obrigatório")
        @Schema(description = "Somente taxas ativas são usadas no cálculo", example = "true")
        Boolean ativo
) {
}
