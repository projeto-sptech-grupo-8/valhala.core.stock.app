package valhalla.core.stock.app.modules.caixa.configuracao.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.caixa.point.client.dto.ModoOperacaoTerminalPoint;

import java.util.UUID;

public record ModoOperacaoTerminalResponseDto(
        @Schema(description = "Identificador do terminal cadastrado no caixa") UUID id,
        @Schema(description = "Identificador do caixa") UUID caixaId,
        @Schema(description = "Identificador externo da maquininha no Mercado Pago") String terminalId,
        @Schema(description = "Nome amigável da maquininha") String nome,
        @Schema(description = "Modo de operação sincronizado no Mercado Pago") ModoOperacaoTerminalPoint modoOperacao
) {
}
