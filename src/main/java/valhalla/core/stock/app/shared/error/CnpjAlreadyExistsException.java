package valhalla.core.stock.app.shared.error;

public class CnpjAlreadyExistsException extends RuntimeException {

    public CnpjAlreadyExistsException(String message) {
        super(message);
    }
}
