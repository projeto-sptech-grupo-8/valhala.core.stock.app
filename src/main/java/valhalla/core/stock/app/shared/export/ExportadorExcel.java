package valhalla.core.stock.app.shared.export;

import java.util.List;

public interface ExportadorExcel {

    <T> byte[] gerarPlanilha(String nomeAba, List<ColunaExcel<T>> colunas, List<T> registros);
}
