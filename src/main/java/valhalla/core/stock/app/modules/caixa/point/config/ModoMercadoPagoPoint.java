package valhalla.core.stock.app.modules.caixa.point.config;

public enum ModoMercadoPagoPoint {
    MOCK,
    HOMOLOGACAO,
    PRODUCAO;

    public boolean usaApiExternaReal() {
        return this == HOMOLOGACAO || this == PRODUCAO;
    }
}
