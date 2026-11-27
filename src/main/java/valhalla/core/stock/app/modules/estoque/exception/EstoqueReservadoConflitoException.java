package valhalla.core.stock.app.modules.estoque.exception;

import java.math.BigDecimal;

public class EstoqueReservadoConflitoException extends RuntimeException {

    private final String produtoNome;
    private final BigDecimal disponivelLivre;
    private final BigDecimal reservado;

    public EstoqueReservadoConflitoException(
            String message,
            String produtoNome,
            BigDecimal disponivelLivre,
            BigDecimal reservado
    ) {
        super(message);
        this.produtoNome = produtoNome;
        this.disponivelLivre = disponivelLivre;
        this.reservado = reservado;
    }

    public String getProdutoNome() {
        return produtoNome;
    }

    public BigDecimal getDisponivelLivre() {
        return disponivelLivre;
    }

    public BigDecimal getReservado() {
        return reservado;
    }
}
