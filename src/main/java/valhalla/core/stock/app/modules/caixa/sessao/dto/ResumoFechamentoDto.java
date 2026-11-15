package valhalla.core.stock.app.modules.caixa.sessao.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Map;

public record ResumoFechamentoDto(
        @Schema(description = "Quantidade de pedidos com pagamento aprovado na sessão", example = "12")
        long qtdVendas,

        @Schema(description = "Soma dos valores aprovados na sessão", example = "850.00")
        BigDecimal totalVendas,

        @Schema(description = "Totais aprovados por forma de pagamento",
                example = "{\"DINHEIRO\":300.00,\"DEBITO\":150.00,\"CREDITO\":250.00,\"PIX\":150.00}")
        Map<String, BigDecimal> porForma,

        @Schema(description = "Total de sangrias", example = "50.00")
        BigDecimal sangrias,

        @Schema(description = "Total de reforços", example = "20.00")
        BigDecimal reforcos,

        @Schema(description = "Fundo de troco da abertura", example = "100.00")
        BigDecimal fundoTroco,

        @Schema(description = "Dinheiro que deveria estar na gaveta", example = "370.00")
        BigDecimal dinheiroEsperado,

        @Schema(description = "Dinheiro contado; nulo enquanto a sessão está aberta", example = "365.00",
                nullable = true)
        BigDecimal dinheiroContado,

        @Schema(description = "Diferença entre contado e esperado; nula enquanto a sessão está aberta",
                example = "-5.00", nullable = true)
        BigDecimal diferenca
) {
}
