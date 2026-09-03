package valhalla.core.stock.app.shared.error;

public class UsuarioNaoAutorizadoException extends RuntimeException {
    public UsuarioNaoAutorizadoException(String message) {
        super(message);
    }
}
