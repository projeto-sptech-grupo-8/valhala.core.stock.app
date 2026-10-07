package valhalla.core.stock.app.modules.estoque.strategy;

import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;

import java.math.BigDecimal;

@Component
public class EstrategiaSaida extends AbstractEstrategiaMovimentacao {

    @Override
    public TipoMovimentacaoEstoque tipoSuportado() {
        return TipoMovimentacaoEstoque.SAIDA;
    }

    @Override
    public void validar(MovimentacaoCommand command) {
    }

    @Override
    public BigDecimal calcularSaldoPosterior(BigDecimal saldoAnterior, BigDecimal quantidade) {
        return subtrairSemPermitirNegativo(saldoAnterior, quantidade);
    }
}
