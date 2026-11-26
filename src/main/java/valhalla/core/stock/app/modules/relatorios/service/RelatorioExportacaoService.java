package valhalla.core.stock.app.modules.relatorios.service;

import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.relatorios.dto.RelatorioLinhas;
import valhalla.core.stock.app.shared.export.ColunaExcel;
import valhalla.core.stock.app.shared.export.ExportadorExcel;
import valhalla.core.stock.app.shared.export.FormatoCelulaExcel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Exportação dos relatórios gerenciais em planilha Excel (.xlsx). */
@Service
public class RelatorioExportacaoService {

    private static final BigDecimal LIMITE_FALTA_RECORRENTE = BigDecimal.TEN;

    private static final List<ColunaExcel<RelatorioLinhas.CurvaAbc>> COLUNAS_CURVA_ABC = List.of(
            new ColunaExcel<>("Posição", FormatoCelulaExcel.INTEIRO, RelatorioLinhas.CurvaAbc::posicao),
            new ColunaExcel<>("Produto", FormatoCelulaExcel.TEXTO, item -> textoOuVazio(item.nome())),
            new ColunaExcel<>("Categoria", FormatoCelulaExcel.TEXTO, item -> textoOuVazio(item.categoria())),
            new ColunaExcel<>("Unidades", FormatoCelulaExcel.DECIMAL, RelatorioLinhas.CurvaAbc::quantidade),
            new ColunaExcel<>("Faturamento", FormatoCelulaExcel.MOEDA, item -> arredondar(item.receita())),
            new ColunaExcel<>("% do total", FormatoCelulaExcel.DECIMAL, item -> arredondar(item.percentual())),
            new ColunaExcel<>("% acumulado", FormatoCelulaExcel.DECIMAL, item -> arredondar(item.acumulado())),
            new ColunaExcel<>("Classe", FormatoCelulaExcel.TEXTO, item -> textoOuVazio(item.classe()))
    );

    private static final List<ColunaExcel<RelatorioLinhas.Ruptura>> COLUNAS_RUPTURA = List.of(
            new ColunaExcel<>("Produto", FormatoCelulaExcel.TEXTO, item -> textoOuVazio(item.nome())),
            new ColunaExcel<>("Categoria", FormatoCelulaExcel.TEXTO, item -> textoOuVazio(item.categoria())),
            new ColunaExcel<>("Dias sem estoque", FormatoCelulaExcel.INTEIRO, RelatorioLinhas.Ruptura::dias),
            new ColunaExcel<>("% dos dias", FormatoCelulaExcel.DECIMAL, item -> arredondar(item.percentualDias())),
            new ColunaExcel<>("Ocorrências", FormatoCelulaExcel.INTEIRO, RelatorioLinhas.Ruptura::ocorrencias),
            new ColunaExcel<>("Unidades não vendidas", FormatoCelulaExcel.DECIMAL, RelatorioLinhas.Ruptura::unidadesPerdidas),
            new ColunaExcel<>("Venda perdida", FormatoCelulaExcel.MOEDA, item -> arredondar(item.vendaPerdida())),
            new ColunaExcel<>("Situação", FormatoCelulaExcel.TEXTO, RelatorioExportacaoService::rotuloSituacaoRuptura)
    );

    private final ExportadorExcel exportadorExcel;

    public RelatorioExportacaoService(ExportadorExcel exportadorExcel) {
        this.exportadorExcel = exportadorExcel;
    }

    public byte[] gerarPlanilhaCurvaAbc(List<RelatorioLinhas.CurvaAbc> itens) {
        return exportadorExcel.gerarPlanilha("Curva ABC", COLUNAS_CURVA_ABC, itens);
    }

    public byte[] gerarPlanilhaRuptura(List<RelatorioLinhas.Ruptura> itens) {
        return exportadorExcel.gerarPlanilha("Falta de Estoque", COLUNAS_RUPTURA, itens);
    }

    private static String rotuloSituacaoRuptura(RelatorioLinhas.Ruptura item) {
        if (item.percentualDias() != null && item.percentualDias().compareTo(LIMITE_FALTA_RECORRENTE) > 0) {
            return "Recorrente";
        }
        return item.dias() > 0 ? "Pontual" : "Sem registro";
    }

    private static BigDecimal arredondar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static String textoOuVazio(String valor) {
        return valor == null ? "" : valor;
    }
}
