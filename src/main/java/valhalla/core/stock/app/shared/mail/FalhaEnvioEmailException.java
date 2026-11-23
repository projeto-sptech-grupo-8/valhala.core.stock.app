package valhalla.core.stock.app.shared.mail;

public class FalhaEnvioEmailException extends RuntimeException {

    public FalhaEnvioEmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
