package valhalla.core.stock.app.modules.estoque.strategy;

import java.math.BigDecimal;

abstract class AbstractEstrategiaMovimentacao implements EstrategiaMovimentacaoEstoque {

    protected BigDecimal somar(BigDecimal saldoAnterior, BigDecimal quantidade) {
        return saldoAnterior.add(quantidade);
    }

    protected BigDecimal subtrairSemPermitirNegativo(BigDecimal saldoAnterior, BigDecimal quantidade) {
        BigDecimal saldoPosterior = saldoAnterior.subtract(quantidade);
        if (saldoPosterior.signum() < 0) {
            throw new IllegalArgumentException("Estoque insuficiente para realizar a movimentação");
        }
        return saldoPosterior;
    }

    protected void exigirMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("O motivo é obrigatório para esta movimentação");
        }
    }
}
