package valhalla.core.stock.app.modules.caixa.fiscal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.AmbienteFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.StatusNotaFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.TipoNotaFiscal;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record NotaFiscalDetalheResponseDto(
        @Schema(description = "Identificador da nota fiscal") UUID id,
        @Schema(description = "Identificador do pedido de origem") UUID pedidoId,
        @Schema(description = "Número sequencial do pedido de origem", nullable = true) Long pedidoNumero,
        @Schema(description = "Tipo da nota fiscal") TipoNotaFiscal tipo,
        @Schema(description = "Modelo fiscal SEFAZ (65 para NFC-e)") String modelo,
        @Schema(description = "Ambiente de emissão") AmbienteFiscal ambiente,
        @Schema(description = "Código do provedor emissor (ex.: INTERNO)") String provedor,
        @Schema(description = "Status atual da nota fiscal") StatusNotaFiscal status,
        @Schema(description = "Número sequencial da nota fiscal") String numero,
        @Schema(description = "Série da nota fiscal") String serie,
        @Schema(description = "Chave de acesso de 44 dígitos") String chaveAcesso,
        @Schema(description = "Protocolo de autorização") String protocoloAutorizacao,
        @Schema(description = "URL de consulta/QR Code do DANFE NFC-e") String urlConsulta,
        @Schema(description = "Nome do consumidor (ou nulo quando não identificado)", nullable = true) String contraparteNome,
        @Schema(description = "CPF ou CNPJ do consumidor (ou nulo quando não identificado)", nullable = true) String contraparteDocumento,
        @Schema(description = "Soma bruta dos produtos") BigDecimal valorProdutos,
        @Schema(description = "Desconto total aplicado") BigDecimal valorDesconto,
        @Schema(description = "Valor líquido total da nota") BigDecimal valorTotal,
        @Schema(description = "Troco total devolvido nos pagamentos em dinheiro") BigDecimal valorTrocoTotal,
        @Schema(description = "Usuário que realizou a emissão", nullable = true) UUID emitidaPorUsuarioId,
        @Schema(description = "Nome do usuário que realizou a emissão", nullable = true) String emitidaPorNome,
        @Schema(description = "Data/hora de emissão") LocalDateTime emitidaEm,
        @Schema(description = "Usuário que realizou o cancelamento", nullable = true) UUID canceladaPorUsuarioId,
        @Schema(description = "Nome do usuário que realizou o cancelamento", nullable = true) String canceladaPorNome,
        @Schema(description = "Data/hora de cancelamento", nullable = true) LocalDateTime canceladaEm,
        @Schema(description = "Justificativa do cancelamento", nullable = true) String motivoCancelamento,
        @Schema(description = "Data/hora de criação do registro") LocalDateTime criadaEm,
        @Schema(description = "Data/hora da última atualização") LocalDateTime atualizadaEm,
        @Schema(description = "Dados do estabelecimento emitente para cabeçalho do DANFE NFC-e") EmitenteNotaFiscalResponseDto emitente,
        @Schema(description = "Itens da nota fiscal para impressão do DANFE NFC-e") List<ItemNotaFiscalResponseDto> itens,
        @Schema(description = "Formas de pagamento aprovadas para impressão do DANFE NFC-e") List<PagamentoNotaFiscalResponseDto> pagamentos
) {
}
