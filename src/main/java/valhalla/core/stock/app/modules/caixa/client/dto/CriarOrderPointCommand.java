package valhalla.core.stock.app.modules.caixa.client.dto;

import java.math.BigDecimal;

public record CriarOrderPointCommand(
        String chaveIdempotencia,
        String referenciaExterna,
        BigDecimal valorCobrado,
        String terminalId,
        String defaultPaymentType,
        Integer parcelas,
        String expirationTime,
        String descricao,
        String installmentsCost
) {
    public CriarOrderPointCommand(
            String chaveIdempotencia,
            String referenciaExterna,
            BigDecimal valorCobrado,
            String terminalId,
            String defaultPaymentType,
            Integer parcelas,
            String expirationTime,
            String descricao
    ) {
        this(
                chaveIdempotencia,
                referenciaExterna,
                valorCobrado,
                terminalId,
                defaultPaymentType,
                parcelas,
                expirationTime,
                descricao,
                "seller"
        );
    }
}
