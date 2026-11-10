package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import valhalla.core.stock.app.modules.caixa.entity.FormaPagamentoCaixa;

import java.math.BigDecimal;

public record RegistrarPagamentoPedidoRequestDto(
        @NotNull(message = "A forma de pagamento é obrigatória")
        @Schema(description = "Forma de pagamento: DINHEIRO, DEBITO, CREDITO ou PIX", example = "DINHEIRO")
        FormaPagamentoCaixa forma,

        @NotNull(message = "O valor do pagamento é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor do pagamento deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "O valor do pagamento deve ter no máximo 2 casas decimais")
        @Schema(description = "Valor da parcela aplicada ao saldo do pedido", example = "50.00")
        BigDecimal valor,

        @Min(value = 1, message = "O número de parcelas deve ser no mínimo 1")
        @Max(value = 24, message = "O número de parcelas deve ser no máximo 24")
        @Schema(description = "Quantidade de parcelas (exclusivo de CREDITO; padrão 1)", example = "1", nullable = true)
        Integer parcelas,

        @DecimalMin(value = "0.01", message = "O valor recebido deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "O valor recebido deve ter no máximo 2 casas decimais")
        @Schema(description = "Valor físico entregue pelo cliente (obrigatório quando forma = DINHEIRO)", example = "100.00", nullable = true)
        BigDecimal valorRecebido,

        @Size(max = 100, message = "A chave de idempotência deve ter no máximo 100 caracteres")
        @Schema(description = "Chave de idempotência para evitar cobranças duplicadas", example = "pag-pedido-10-01", nullable = true)
        String chaveIdempotencia,

        @Size(max = 255, message = "A referência do comprovante deve ter no máximo 255 caracteres")
        @Schema(description = "NSU, código de autorização ou referência do comprovante em pagamento manual", nullable = true)
        String comprovanteReferencia
) {
}
