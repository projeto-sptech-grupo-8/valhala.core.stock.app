package valhalla.core.stock.app.shared.pagination;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginacaoTest {

    @Test
    void adicionaDesempateDeterministicoPorIdQuandoOrdenaPorOutroCampo() {
        Pageable pageable = Paginacao.criar(1, 25, "nome", DirecaoOrdenacao.DESC, Set.of("nome", "criadoEm"));

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(25);
        List<Sort.Order> ordens = pageable.getSort().toList();
        assertThat(ordens).containsExactly(
                Sort.Order.desc("nome"),
                Sort.Order.desc("id")
        );
    }

    @Test
    void naoDuplicaIdQuandoOrdenacaoPrincipalJaEhId() {
        Pageable pageable = Paginacao.criar(0, 10, "id", DirecaoOrdenacao.ASC, Set.of("id", "nome"));

        assertThat(pageable.getSort().toList()).containsExactly(Sort.Order.asc("id"));
    }

    @Test
    void rejeitaCampoNaoPermitido() {
        assertThatThrownBy(() -> Paginacao.criar(0, 10, "ativo", DirecaoOrdenacao.ASC, Set.of("nome")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Campo de ordenação inválido: ativo");
    }
}
