package valhalla.core.stock.app.modules.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Vendas de hoje e referência para a meta diária. Nulo para quem não pode ver vendas.")
public record VendasDashboardResponseDto(
        long vendasHoje,
        BigDecimal faturamentoHoje,
        @Schema(description = "Nulo sem vendas hoje.")
        BigDecimal ticketMedioHoje,
        @Schema(description = "Recebido hoje por forma de pagamento (dinheiro já sem o troco).")
        List<FormaPagamento> porFormaPagamento,
        @Schema(description = "Faturamento médio por dia nos 30 dias anteriores a hoje (base da meta diária).")
        BigDecimal mediaDiariaUltimos30Dias
) {

    @Schema(description = "forma: DINHEIRO, DEBITO, CREDITO ou PIX.")
    public record FormaPagamento(String forma, BigDecimal valor) {
    }
}
