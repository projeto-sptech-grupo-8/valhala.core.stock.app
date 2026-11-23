package valhalla.core.stock.app.modules.financeiro.entity;

/** Categoria das contas a pagar, que define o tratamento no resultado (DRE). */
public enum CategoriaConta {
    /** Compra de estoque: só fluxo de caixa; o custo entra no DRE pelo CMV das vendas. */
    MERCADORIA,
    DESPESA_FIXA,
    DESPESA_VARIAVEL
}
