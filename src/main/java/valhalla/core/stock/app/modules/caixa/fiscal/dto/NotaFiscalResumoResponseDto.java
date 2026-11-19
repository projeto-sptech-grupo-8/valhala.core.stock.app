package valhalla.core.stock.app.modules.caixa.fiscal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.AmbienteFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.StatusNotaFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.TipoNotaFiscal;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record NotaFiscalResumoResponseDto(
        @Schema(description = "Identificador da nota fiscal") UUID id,
        @Schema(description = "Identificador do pedido de origem") UUID pedidoId,
        @Schema(description = "Número sequencial do pedido de origem", nullable = true) Long pedidoNumero,
        @Schema(description = "Tipo da nota fiscal") TipoNotaFiscal tipo,
        @Schema(description = "Modelo fiscal SEFAZ (65 para NFC-e)") String modelo,
        @Schema(description = "Ambiente de emissão") AmbienteFiscal ambiente,
        @Schema(description = "Status atual da nota fiscal") StatusNotaFiscal status,
        @Schema(description = "Número sequencial da nota fiscal") String numero,
        @Schema(description = "Série da nota fiscal") String serie,
        @Schema(description = "Chave de acesso de 44 dígitos") String chaveAcesso,
        @Schema(description = "Protocolo de autorização") String protocoloAutorizacao,
        @Schema(description = "Nome do consumidor (ou nulo quando não identificado)", nullable = true) String contraparteNome,
        @Schema(description = "CPF ou CNPJ do consumidor (ou nulo quando não identificado)", nullable = true) String contraparteDocumento,
        @Schema(description = "Soma bruta dos produtos") BigDecimal valorProdutos,
        @Schema(description = "Desconto total aplicado") BigDecimal valorDesconto,
        @Schema(description = "Valor líquido total da nota") BigDecimal valorTotal,
        @Schema(description = "Data/hora de emissão") LocalDateTime emitidaEm,
        @Schema(description = "Data/hora de cancelamento (quando cancelada)", nullable = true) LocalDateTime canceladaEm
) {
}
