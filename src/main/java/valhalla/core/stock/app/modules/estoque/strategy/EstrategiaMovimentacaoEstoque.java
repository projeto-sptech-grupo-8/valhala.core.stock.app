package valhalla.core.stock.app.modules.estoque.strategy;

import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;

import java.math.BigDecimal;

public interface EstrategiaMovimentacaoEstoque {

    TipoMovimentacaoEstoque tipoSuportado();

    void validar(MovimentacaoCommand command);

    BigDecimal calcularSaldoPosterior(BigDecimal saldoAnterior, BigDecimal quantidade);
}
