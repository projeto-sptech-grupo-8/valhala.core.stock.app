package valhalla.core.stock.app.modules.caixa.entity;

public enum StatusPedidoCaixa {
    EM_ATENDIMENTO,
    AGUARDANDO_MAQUINA,
    AGUARDANDO_PAGAMENTO,
    PAGAMENTO_PARCIAL,
    CONCLUIDO,
    CANCELADO,
    ESTORNADO;

    public boolean permiteEdicaoItens() {
        return this == EM_ATENDIMENTO;
    }

    public boolean permitePagamento() {
        return this == EM_ATENDIMENTO
                || this == AGUARDANDO_MAQUINA
                || this == AGUARDANDO_PAGAMENTO
                || this == PAGAMENTO_PARCIAL;
    }

    public boolean estaAberto() {
        return this == EM_ATENDIMENTO
                || this == AGUARDANDO_MAQUINA
                || this == AGUARDANDO_PAGAMENTO
                || this == PAGAMENTO_PARCIAL;
    }
}
