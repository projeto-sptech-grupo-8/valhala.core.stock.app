package valhalla.core.stock.app.modules.caixa.pedido.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import valhalla.core.stock.app.modules.caixa.pedido.enums.TipoDescontoPedido;

import java.math.BigDecimal;

public record DescontoPedidoCaixaRequestDto(
        @NotNull(message = "O tipo de desconto é obrigatório")
        @Schema(description = "Tipo do desconto: PERCENTUAL ou VALOR", example = "PERCENTUAL")
        TipoDescontoPedido tipoDesconto,

        @NotNull(message = "O valor do desconto é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor do desconto deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "O valor do desconto deve ter no máximo 2 casas decimais")
        @Schema(description = "Percentual (0.01 a 100) ou valor em reais conforme o tipoDesconto", example = "15.00")
        BigDecimal valorDescontoInformado,

        @Size(max = 500, message = "O motivo do desconto deve ter no máximo 500 caracteres")
        @Schema(description = "Motivo do desconto (obrigatório quando acima do limite sem autorização direta)", nullable = true)
        String motivo,

        @Schema(description = "E-mail do gerente/autorizador para aprovação imediata no balcão", nullable = true)
        String autorizadorEmail,

        @Schema(description = "Senha do gerente/autorizador para aprovação imediata no balcão", nullable = true)
        String autorizadorSenha
) {
}
