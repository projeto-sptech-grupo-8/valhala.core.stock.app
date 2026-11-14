package valhalla.core.stock.app.modules.caixa.exception;

public class RateLimitExcedidoException extends RuntimeException {
    public RateLimitExcedidoException(String message) {
        super(message);
    }
}
