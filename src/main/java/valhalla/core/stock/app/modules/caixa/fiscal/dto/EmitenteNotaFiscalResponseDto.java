package valhalla.core.stock.app.modules.caixa.fiscal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record EmitenteNotaFiscalResponseDto(
        @Schema(description = "Identificador do estabelecimento emitente") UUID estabelecimentoId,
        @Schema(description = "Razão social do emitente") String razaoSocial,
        @Schema(description = "Nome fantasia do emitente") String nomeFantasia,
        @Schema(description = "CNPJ do emitente (14 dígitos)") String cnpj
) {
}
