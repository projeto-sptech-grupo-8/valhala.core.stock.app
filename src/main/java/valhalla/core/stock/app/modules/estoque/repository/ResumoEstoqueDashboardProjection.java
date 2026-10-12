package valhalla.core.stock.app.modules.estoque.repository;

public interface ResumoEstoqueDashboardProjection {

    long getTotalProdutos();

    long getProdutosComMinimo();

    long getProdutosSemMinimo();

    long getZerados();

    long getCriticos();

    long getSaudaveis();
}
