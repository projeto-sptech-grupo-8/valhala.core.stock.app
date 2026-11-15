package valhalla.core.stock.app.modules.estoque.strategy;

/** Efeito de um tipo de movimentação nos lotes do estoque. */
public enum PoliticaLote {
    /** Soma no lote informado (ou SEM-LOTE). */
    CREDITO,
    /** Consome por FEFO só lotes dentro da validade: venda e consumo não usam produto vencido. */
    DEBITO_SEM_VENCIDOS,
    /** Consome por FEFO incluindo vencidos (que saem primeiro): perda e correção de saldo. */
    DEBITO_COM_VENCIDOS
}
