package valhalla.core.stock.app.modules.caixa.fiscal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.StatusNotaFiscal;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotaFiscalPedidoVinculadaDto(
        @Schema(description = "Identificador da nota fiscal vinculada ao pedido") UUID id,
        @Schema(description = "Status da nota fiscal (AUTORIZADA ou CANCELADA)") StatusNotaFiscal status,
        @Schema(description = "Número sequencial da NFC-e") String numero,
        @Schema(description = "Série da NFC-e") String serie,
        @Schema(description = "Chave de acesso de 44 dígitos") String chaveAcesso,
        @Schema(description = "Data/hora de emissão") LocalDateTime emitidaEm
) {
}
