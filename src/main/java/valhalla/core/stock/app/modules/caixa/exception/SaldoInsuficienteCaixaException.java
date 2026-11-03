package valhalla.core.stock.app.modules.caixa.exception;

import java.math.BigDecimal;

public class SaldoInsuficienteCaixaException extends RuntimeException {

    private final BigDecimal disponivel;

    public SaldoInsuficienteCaixaException(String message, BigDecimal disponivel) {
        super(message);
        this.disponivel = disponivel;
    }

    public BigDecimal getDisponivel() {
        return disponivel;
    }
}
