package valhalla.core.stock.app.modules.caixa.config;

public enum ModoMercadoPagoPoint {
    MOCK,
    HOMOLOGACAO,
    PRODUCAO;

    public boolean usaApiExternaReal() {
        return this == HOMOLOGACAO || this == PRODUCAO;
    }
}
