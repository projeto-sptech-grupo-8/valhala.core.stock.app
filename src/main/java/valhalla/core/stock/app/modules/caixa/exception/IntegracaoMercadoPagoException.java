package valhalla.core.stock.app.modules.caixa.exception;

import org.springframework.http.HttpStatus;

public class IntegracaoMercadoPagoException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final boolean erroTransitorio;

    public IntegracaoMercadoPagoException(String message, HttpStatus httpStatus, boolean erroTransitorio) {
        super(message);
        this.httpStatus = httpStatus;
        this.erroTransitorio = erroTransitorio;
    }

    public IntegracaoMercadoPagoException(
            String message,
            Throwable cause,
            HttpStatus httpStatus,
            boolean erroTransitorio
    ) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.erroTransitorio = erroTransitorio;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public boolean isErroTransitorio() {
        return erroTransitorio;
    }
}
