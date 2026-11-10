package valhalla.core.stock.app.modules.caixa.exception;

import java.math.BigDecimal;

public class EstoqueInsuficientePedidoException extends RuntimeException {

    private final String produtoNome;
    private final BigDecimal disponivel;

    public EstoqueInsuficientePedidoException(
            String message,
            String produtoNome,
            BigDecimal disponivel
    ) {
        super(message);
        this.produtoNome = produtoNome;
        this.disponivel = disponivel;
    }

    public String getProdutoNome() {
        return produtoNome;
    }

    public BigDecimal getDisponivel() {
        return disponivel;
    }
}
