package valhalla.core.stock.app.modules.caixa.exception;

import java.util.Map;

public class PagamentoPendenteFechamentoException extends RuntimeException {

    private final Map<String, Long> pendenciasPorStatus;

    public PagamentoPendenteFechamentoException(String message, Map<String, Long> pendenciasPorStatus) {
        super(message);
        this.pendenciasPorStatus = pendenciasPorStatus;
    }

    public Map<String, Long> getPendenciasPorStatus() {
        return pendenciasPorStatus;
    }
}
