package valhalla.core.stock.app.shared.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Set;

public final class Paginacao {

    public static final int PAGINA_PADRAO = 0;
    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 100;

    private Paginacao() {
    }

    public static Pageable criar(
            int pagina,
            int tamanho,
            String ordenarPor,
            DirecaoOrdenacao direcao,
            Set<String> camposPermitidos
    ) {
        if (!camposPermitidos.contains(ordenarPor)) {
            throw new IllegalArgumentException("Campo de ordenação inválido: " + ordenarPor);
        }
        return PageRequest.of(pagina, tamanho, direcao.paraSpringData(), ordenarPor);
    }
}
