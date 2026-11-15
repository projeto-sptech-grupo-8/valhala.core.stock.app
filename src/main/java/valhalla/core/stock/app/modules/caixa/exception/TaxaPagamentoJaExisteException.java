package valhalla.core.stock.app.modules.caixa.exception;

public class TaxaPagamentoJaExisteException extends RuntimeException {

    public TaxaPagamentoJaExisteException(String message) {
        super(message);
    }
}
