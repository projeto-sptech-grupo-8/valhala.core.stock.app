package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.caixa.entity.PoliticaJurosCaixa;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ConfiguracaoCaixaResponseDto(
        @Schema(description = "Identificador da configuração") UUID id,
        @Schema(description = "Cobrança na Point habilitada") boolean pointHabilitado,
        @Schema(description = "Pagamento em dinheiro habilitado") boolean dinheiroHabilitado,
        @Schema(description = "Pagamento em débito habilitado") boolean debitoHabilitado,
        @Schema(description = "Pagamento em crédito habilitado") boolean creditoHabilitado,
        @Schema(description = "Pagamento via Pix habilitado") boolean pixHabilitado,
        @Schema(description = "Parcelamento habilitado") boolean parcelamentoHabilitado,
        @Schema(description = "Número mínimo de parcelas") int parcelasMinimas,
        @Schema(description = "Número máximo de parcelas") int parcelasMaximas,
        @Schema(description = "Valor mínimo por parcela") BigDecimal valorMinimoParcela,
        @Schema(description = "Política de juros do parcelamento") PoliticaJurosCaixa politicaJuros,
        @Schema(description = "Validade da cobrança em segundos") int validadeCobrancaSegundos,
        @Schema(description = "Limite percentual de desconto sem autorização") BigDecimal limiteDescontoPercentual,
        @Schema(description = "Limite em valor de desconto sem autorização") BigDecimal limiteDescontoValor,
        @Schema(description = "Impressão de comprovante habilitada") boolean imprimirComprovante,
        @Schema(description = "Pagamento manual habilitado") boolean permitirPagamentoManual,
        @Schema(description = "Último usuário que alterou a configuração") UUID atualizadoPorUsuarioId,
        @Schema(description = "Data de criação") LocalDateTime criadoEm,
        @Schema(description = "Data da última alteração") LocalDateTime atualizadoEm
) {
}
