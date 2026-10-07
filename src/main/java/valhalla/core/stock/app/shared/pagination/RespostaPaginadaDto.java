package valhalla.core.stock.app.shared.pagination;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record RespostaPaginadaDto<T>(
        List<T> itens,
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas,
        boolean primeira,
        boolean ultima
) {
    public static <E, T> RespostaPaginadaDto<T> de(Page<E> pagina, Function<E, T> mapper) {
        return new RespostaPaginadaDto<>(
                pagina.getContent().stream().map(mapper).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages(),
                pagina.isFirst(),
                pagina.isLast()
        );
    }
}
