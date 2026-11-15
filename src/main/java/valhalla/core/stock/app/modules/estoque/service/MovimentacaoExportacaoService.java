package valhalla.core.stock.app.modules.estoque.service;

import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.estoque.dto.MovimentacaoResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;
import valhalla.core.stock.app.shared.export.ColunaExcel;
import valhalla.core.stock.app.shared.export.ExportadorExcel;
import valhalla.core.stock.app.shared.export.FormatoCelulaExcel;

import java.util.List;

@Service
public class MovimentacaoExportacaoService {

    private static final List<ColunaExcel<MovimentacaoResponseDto>> COLUNAS = List.of(
            new ColunaExcel<>("Data/Hora", FormatoCelulaExcel.DATA_HORA, MovimentacaoResponseDto::ocorridoEm),
            new ColunaExcel<>("Produto", FormatoCelulaExcel.TEXTO, m -> textoOuVazio(m.produtoNome())),
            new ColunaExcel<>("Tipo", FormatoCelulaExcel.TEXTO, m -> rotuloTipo(m.tipo())),
            new ColunaExcel<>("Quantidade", FormatoCelulaExcel.DECIMAL, MovimentacaoResponseDto::quantidade),
            new ColunaExcel<>("Unidade", FormatoCelulaExcel.TEXTO, m -> textoOuVazio(m.unidadeEstoque())),
            new ColunaExcel<>("Saldo Anterior", FormatoCelulaExcel.DECIMAL, MovimentacaoResponseDto::saldoAnterior),
            new ColunaExcel<>("Saldo Posterior", FormatoCelulaExcel.DECIMAL, MovimentacaoResponseDto::saldoPosterior),
            new ColunaExcel<>("Operador", FormatoCelulaExcel.TEXTO, m -> textoOuVazio(m.usuarioNome())),
            new ColunaExcel<>("Lote", FormatoCelulaExcel.TEXTO, m -> textoOuVazio(m.lote())),
            new ColunaExcel<>("Nota Fiscal", FormatoCelulaExcel.TEXTO, m -> textoOuVazio(m.numeroNotaFiscal())),
            new ColunaExcel<>("Motivo / Observação", FormatoCelulaExcel.TEXTO, m -> textoOuVazio(m.motivo()))
    );

    private final ExportadorExcel exportadorExcel;

    public MovimentacaoExportacaoService(ExportadorExcel exportadorExcel) {
        this.exportadorExcel = exportadorExcel;
    }

    public byte[] gerarPlanilhaExcel(List<MovimentacaoResponseDto> movimentacoes) {
        return exportadorExcel.gerarPlanilha("Movimentações", COLUNAS, movimentacoes);
    }

    private static String rotuloTipo(TipoMovimentacaoEstoque tipo) {
        if (tipo == null) {
            return "";
        }
        return switch (tipo) {
            case ENTRADA_INICIAL -> "Entrada Inicial";
            case ENTRADA -> "Entrada";
            case SAIDA -> "Saída";
            case SAIDA_DRINK -> "Saída (Drink)";
            case PERDA -> "Perda";
            case AJUSTE_POSITIVO -> "Ajuste Positivo";
            case AJUSTE_NEGATIVO -> "Ajuste Negativo";
        };
    }

    private static String textoOuVazio(String valor) {
        return valor == null ? "" : valor;
    }
}
