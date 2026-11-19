package valhalla.core.stock.app.modules.caixa.fiscal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PagamentoNotaFiscalResponseDto(
        @Schema(description = "Identificador do pagamento no pedido") UUID id,
        @Schema(description = "Forma de pagamento") FormaPagamentoCaixa forma,
        @Schema(description = "Código da forma de pagamento no padrão SEFAZ (01=Dinheiro, 03=Crédito, 04=Débito, 17=PIX)") String codigoSefaz,
        @Schema(description = "Valor líquido aplicado à venda") BigDecimal valor,
        @Schema(description = "Valor físico recebido (para pagamentos em dinheiro)", nullable = true) BigDecimal valorRecebido,
        @Schema(description = "Troco devolvido ao cliente") BigDecimal valorTroco,
        @Schema(description = "Quantidade de parcelas") Integer parcelas,
        @Schema(description = "Data/hora da aprovação do pagamento", nullable = true) LocalDateTime pagoEm
) {
}
