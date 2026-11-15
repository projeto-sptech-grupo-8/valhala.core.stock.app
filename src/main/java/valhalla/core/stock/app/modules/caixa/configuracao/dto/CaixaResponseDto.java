package valhalla.core.stock.app.modules.caixa.configuracao.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CaixaResponseDto(
        @Schema(description = "Identificador do caixa") UUID id,
        @Schema(description = "Nome do caixa") String nome,
        @Schema(description = "Caixa ativo") boolean ativo,
        @Schema(description = "Existe uma sessão ABERTA neste caixa") boolean emUso,
        @Schema(description = "Maquininhas vinculadas ao caixa") List<TerminalCaixaResponseDto> terminais,
        @Schema(description = "Data de criação") LocalDateTime criadoEm,
        @Schema(description = "Data da última alteração") LocalDateTime atualizadoEm
) {
}
