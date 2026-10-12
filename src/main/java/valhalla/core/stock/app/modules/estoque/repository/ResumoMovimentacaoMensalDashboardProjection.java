package valhalla.core.stock.app.modules.estoque.repository;

public interface ResumoMovimentacaoMensalDashboardProjection {

    Integer getAno();

    Integer getMes();

    long getEntradas();

    long getSaidas();
}
