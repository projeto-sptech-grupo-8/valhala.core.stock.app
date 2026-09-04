package valhalla.core.stock.app.shared.error;

public class UserDeletionConflictException extends RuntimeException {

    public UserDeletionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
