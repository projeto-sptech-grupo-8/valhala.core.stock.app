package valhalla.core.stock.app.shared.export;

import java.util.function.Function;

public record ColunaExcel<T>(
        String cabecalho,
        FormatoCelulaExcel formato,
        Function<T, Object> extrator
) {
}
