package valhalla.core.stock.app.modules.vendas.entity;

/** Canal da venda (coluna pedido.tipo). */
public enum CanalVenda {
    BALCAO("Balcão"),
    MESA("Mesa"),
    DELIVERY("Delivery");

    private final String rotulo;

    CanalVenda(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
