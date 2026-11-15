package valhalla.core.stock.app.modules.estoque.service;

import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.shared.export.ColunaExcel;
import valhalla.core.stock.app.shared.export.ExportadorExcel;
import valhalla.core.stock.app.shared.export.FormatoCelulaExcel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ProdutoExportacaoService {

    private static final List<ColunaExcel<ProdutoResponseDto>> COLUNAS = List.of(
            new ColunaExcel<>("SKU", FormatoCelulaExcel.TEXTO, p -> textoOuVazio(p.sku())),
            new ColunaExcel<>("Nome", FormatoCelulaExcel.TEXTO, p -> textoOuVazio(p.nome())),
            new ColunaExcel<>("Tipo", FormatoCelulaExcel.TEXTO, p -> p.tipo() == ProdutoTipo.DRINK ? "Drink" : "Padrão"),
            new ColunaExcel<>("Categoria", FormatoCelulaExcel.TEXTO, p -> textoOuVazio(p.categoriaNome())),
            new ColunaExcel<>("Unidade", FormatoCelulaExcel.TEXTO, p -> textoOuVazio(p.unidadeMedida())),
            new ColunaExcel<>("Fardo", FormatoCelulaExcel.TEXTO, p -> Boolean.TRUE.equals(p.fardo()) ? "Sim" : "Não"),
            new ColunaExcel<>("Unid. por Fardo", FormatoCelulaExcel.INTEIRO, ProdutoResponseDto::unidadesPorFardo),
            new ColunaExcel<>("Fracionado", FormatoCelulaExcel.TEXTO, p -> Boolean.TRUE.equals(p.fracionado()) ? "Sim" : "Não"),
            new ColunaExcel<>("Volume Embalagem (ml)", FormatoCelulaExcel.DECIMAL, ProdutoResponseDto::volumeEmbalagemMl),
            new ColunaExcel<>("Quantidade em Estoque", FormatoCelulaExcel.DECIMAL,
                    p -> p.tipo() == ProdutoTipo.DRINK ? null : converterParaUnidadeExibicao(p, p.quantidadeEstoque())),
            new ColunaExcel<>("Estoque Mínimo", FormatoCelulaExcel.DECIMAL,
                    p -> p.tipo() == ProdutoTipo.DRINK ? null : converterParaUnidadeExibicao(p, p.estoqueMinimo())),
            new ColunaExcel<>("Preço de Custo", FormatoCelulaExcel.MOEDA,
                    p -> p.tipo() == ProdutoTipo.DRINK ? null : p.precoCusto()),
            new ColunaExcel<>("Preço de Venda", FormatoCelulaExcel.MOEDA, ProdutoResponseDto::precoVenda),
            new ColunaExcel<>("Margem (%)", FormatoCelulaExcel.DECIMAL, ProdutoResponseDto::margemPercentual),
            new ColunaExcel<>("Situação", FormatoCelulaExcel.TEXTO, p -> Boolean.TRUE.equals(p.ativo()) ? "Ativo" : "Inativo")
    );

    private final ExportadorExcel exportadorExcel;

    public ProdutoExportacaoService(ExportadorExcel exportadorExcel) {
        this.exportadorExcel = exportadorExcel;
    }

    public byte[] gerarPlanilhaExcel(List<ProdutoResponseDto> produtos) {
        return exportadorExcel.gerarPlanilha("Produtos", COLUNAS, produtos);
    }

    private static BigDecimal converterParaUnidadeExibicao(ProdutoResponseDto produto, BigDecimal valorBase) {
        if (valorBase == null) {
            return null;
        }
        if (Boolean.TRUE.equals(produto.fracionado())
                && produto.volumeEmbalagemMl() != null
                && produto.volumeEmbalagemMl().signum() > 0) {
            return valorBase.divide(produto.volumeEmbalagemMl(), 3, RoundingMode.HALF_UP);
        }
        return valorBase;
    }

    private static String textoOuVazio(String valor) {
        return valor == null ? "" : valor;
    }
}
