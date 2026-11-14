package valhalla.core.stock.app.modules.caixa.point.client.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TerminalMercadoPagoDto(
        @Schema(description = "Identificador único do terminal na API do Mercado Pago", example = "PAX_A910__SMARTPOS1234567890")
        String id,
        @Schema(description = "Identificador do POS vinculado", nullable = true)
        String posId,
        @Schema(description = "Identificador da loja vinculada", nullable = true)
        String storeId,
        @Schema(description = "Referência externa do POS", nullable = true)
        String externalPosId,
        @Schema(description = "Modo de operação atual: PDV ou STANDALONE", example = "PDV")
        String operatingMode
) {
}
