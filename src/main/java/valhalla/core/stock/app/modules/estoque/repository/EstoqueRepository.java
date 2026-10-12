package valhalla.core.stock.app.modules.estoque.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EstoqueRepository extends JpaRepository<EstoqueEntity, Integer> {

    Optional<EstoqueEntity> findByProdutoId(UUID idProduto);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EstoqueEntity e join fetch e.produto where e.produto.id = :idProduto")
    Optional<EstoqueEntity> findByProdutoIdForUpdate(UUID idProduto);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EstoqueEntity e join fetch e.produto where e.produto.id in :idsProdutos order by e.produto.id")
    List<EstoqueEntity> findAllByProdutoIdInForUpdate(Collection<UUID> idsProdutos);

    List<EstoqueEntity> findAllByProdutoIdIn(Collection<UUID> idsProdutos);

    @Query("""
            select count(e) as totalProdutos,
                   coalesce(sum(case when e.estoqueMinimo > 0 then 1 else 0 end), 0) as produtosComMinimo,
                   coalesce(sum(case when e.estoqueMinimo is null or e.estoqueMinimo <= 0 then 1 else 0 end), 0) as produtosSemMinimo,
                   coalesce(sum(case when e.estoqueMinimo > 0 and e.quantidadeAtual <= 0 then 1 else 0 end), 0) as zerados,
                   coalesce(sum(case when e.estoqueMinimo > 0 and e.quantidadeAtual > 0 and e.quantidadeAtual < e.estoqueMinimo then 1 else 0 end), 0) as criticos,
                   coalesce(sum(case when e.estoqueMinimo > 0 and e.quantidadeAtual >= e.estoqueMinimo * 1.5 then 1 else 0 end), 0) as saudaveis
            from EstoqueEntity e
            join e.produto p
            where p.estabelecimentoId = :idEstabelecimento
              and p.ativo = true
            """)
    ResumoEstoqueDashboardProjection resumirParaDashboard(@Param("idEstabelecimento") UUID idEstabelecimento);

    @Query("""
            select e from EstoqueEntity e
            join fetch e.produto p
            join fetch p.categoria c
            where p.estabelecimentoId = :idEstabelecimento
              and p.ativo = true
              and e.estoqueMinimo > 0
              and e.quantidadeAtual < e.estoqueMinimo
            order by case when e.quantidadeAtual <= 0 then 0 else 1 end asc,
                     (e.quantidadeAtual / e.estoqueMinimo) asc,
                     p.nome asc
            """)
    List<EstoqueEntity> listarCriticosParaDashboard(
            @Param("idEstabelecimento") UUID idEstabelecimento,
            Pageable pageable
    );

    long deleteByProdutoId(UUID idProduto);
}
