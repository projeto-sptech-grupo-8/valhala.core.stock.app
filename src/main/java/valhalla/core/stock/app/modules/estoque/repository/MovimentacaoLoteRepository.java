package valhalla.core.stock.app.modules.estoque.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoLoteEntity;

import java.util.Collection;
import java.util.List;

public interface MovimentacaoLoteRepository extends JpaRepository<MovimentacaoLoteEntity, Long> {

    @EntityGraph(attributePaths = {"lote"})
    List<MovimentacaoLoteEntity> findAllByMovimentacaoIdInOrderByIdAsc(Collection<Long> idsMovimentacoes);
}
