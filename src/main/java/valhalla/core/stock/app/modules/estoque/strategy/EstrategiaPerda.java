package valhalla.core.stock.app.modules.estoque.strategy;

import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;

import java.math.BigDecimal;

@Component
public class EstrategiaPerda extends AbstractEstrategiaMovimentacao {

    @Override
    public TipoMovimentacaoEstoque tipoSuportado() {
        return TipoMovimentacaoEstoque.PERDA;
    }

    @Override
    public void validar(MovimentacaoCommand command) {
        exigirMotivo(command.motivo());
    }

    @Override
    public BigDecimal calcularSaldoPosterior(BigDecimal saldoAnterior, BigDecimal quantidade) {
        return subtrairSemPermitirNegativo(saldoAnterior, quantidade);
    }
}
