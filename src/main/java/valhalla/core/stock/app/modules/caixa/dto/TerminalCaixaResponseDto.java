package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

public record TerminalCaixaResponseDto(
        @Schema(description = "Identificador do vínculo terminal e caixa") UUID id,
        @Schema(description = "Caixa ao qual a maquininha pertence") UUID caixaId,
        @Schema(description = "Identificador do dispositivo no Mercado Pago") String terminalId,
        @Schema(description = "Nome amigável da maquininha") String nome,
        @Schema(description = "Terminal ativo") boolean ativo,
        @Schema(description = "Data de criação") LocalDateTime criadoEm,
        @Schema(description = "Data da última alteração") LocalDateTime atualizadoEm
) {
}
