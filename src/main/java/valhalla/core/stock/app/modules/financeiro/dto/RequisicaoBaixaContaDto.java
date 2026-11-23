package valhalla.core.stock.app.modules.financeiro.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Baixa (pagamento ou recebimento) de uma conta em aberto.")
public record RequisicaoBaixaContaDto(
        @Schema(description = "Data do pagamento/recebimento. Omitida: hoje. Não pode ser futura.", example = "2026-11-17")
        LocalDate pagoEm,

        @Positive(message = "O valor pago deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "O valor deve ter no máximo 2 casas decimais")
        @Schema(description = "Valor efetivamente pago, com juros ou desconto. Omitido: o valor da conta.", example = "3800.00")
        BigDecimal valorPago
) {
}
