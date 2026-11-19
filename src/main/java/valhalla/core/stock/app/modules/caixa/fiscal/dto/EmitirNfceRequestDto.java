package valhalla.core.stock.app.modules.caixa.fiscal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record EmitirNfceRequestDto(
        @Schema(description = "Identificador do pedido concluído no caixa")
        @NotNull(message = "O identificador do pedido é obrigatório")
        UUID pedidoId,

        @Schema(
                description = "CPF (11 dígitos) ou CNPJ (14 dígitos) do consumidor na nota (opcional, com ou sem máscara)",
                example = "12345678909",
                nullable = true
        )
        @Size(max = 20, message = "O documento do cliente deve ter no máximo 20 caracteres")
        String documentoCliente,

        @Schema(
                description = "Nome ou razão social do consumidor na nota (opcional)",
                example = "João da Silva",
                nullable = true
        )
        @Size(max = 150, message = "O nome do cliente deve ter no máximo 150 caracteres")
        String nomeCliente
) {
}
