package valhalla.core.stock.app.shared.error;

public class InvalidUserUpdateException extends RuntimeException {

    public InvalidUserUpdateException(String message) {
        super(message);
    }
}
