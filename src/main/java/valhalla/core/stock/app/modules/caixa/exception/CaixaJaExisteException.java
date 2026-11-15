package valhalla.core.stock.app.modules.caixa.exception;

public class CaixaJaExisteException extends RuntimeException {

    public CaixaJaExisteException(String message) {
        super(message);
    }
}
