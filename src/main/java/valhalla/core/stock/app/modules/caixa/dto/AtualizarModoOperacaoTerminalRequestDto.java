package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import valhalla.core.stock.app.modules.caixa.client.dto.ModoOperacaoTerminalPoint;

public record AtualizarModoOperacaoTerminalRequestDto(
        @NotNull(message = "O modo de operação é obrigatório")
        @Schema(description = "Modo de operação desejado na Point Smart 2: PDV ou STANDALONE", example = "PDV")
        ModoOperacaoTerminalPoint modoOperacao
) {
}
