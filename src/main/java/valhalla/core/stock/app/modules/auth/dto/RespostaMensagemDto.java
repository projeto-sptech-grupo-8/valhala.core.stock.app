package valhalla.core.stock.app.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RespostaMensagemDto(
        @Schema(description = "Mensagem descritiva do resultado da operação")
        String mensagem
) {
}
