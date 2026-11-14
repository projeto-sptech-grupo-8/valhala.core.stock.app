package valhalla.core.stock.app.modules.caixa.configuracao.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TaxaPagamentoResponseDto(
        @Schema(description = "Identificador da taxa") UUID id,
        @Schema(description = "Forma de pagamento") FormaPagamentoCaixa forma,
        @Schema(description = "Número de parcelas") int parcelas,
        @Schema(description = "Percentual total sobre o valor da venda") BigDecimal taxaPercentual,
        @Schema(description = "Valor fixo por transação") BigDecimal taxaFixa,
        @Schema(description = "Configuração própria de repasse; nulo segue a política global",
                nullable = true) Boolean repassarAoCliente,
        @Schema(description = "Resultado final: a taxa é repassada ao cliente considerando a política global")
        boolean repasseEfetivo,
        @Schema(description = "Taxa ativa") boolean ativo,
        @Schema(description = "Data de criação") LocalDateTime criadoEm,
        @Schema(description = "Data da última alteração") LocalDateTime atualizadoEm
) {
}
