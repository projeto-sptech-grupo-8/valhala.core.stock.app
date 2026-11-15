package valhalla.core.stock.app.modules.estoque.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import valhalla.core.stock.app.modules.estoque.entity.LoteEstoqueEntity;

import java.util.List;
import java.util.UUID;

public interface LoteEstoqueRepository extends JpaRepository<LoteEstoqueEntity, Long>, JpaSpecificationExecutor<LoteEstoqueEntity> {

    List<LoteEstoqueEntity> findAllByEstoqueId(Integer idEstoque);

    @Override
    @EntityGraph(attributePaths = {"estoque", "estoque.produto"})
    Page<LoteEstoqueEntity> findAll(Specification<LoteEstoqueEntity> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"estoque", "estoque.produto"})
    List<LoteEstoqueEntity> findAll(Specification<LoteEstoqueEntity> specification);

    /** Produtos do estabelecimento cujo saldo não bate com a soma dos lotes (ex.: saldo anterior aos lotes). */
    @Query("""
            select e.produto.id from EstoqueEntity e
            where e.produto.estabelecimentoId = :idEstabelecimento
              and e.quantidadeAtual <> coalesce(
                  (select sum(l.quantidadeAtual) from LoteEstoqueEntity l where l.estoque = e), 0)
            """)
    List<UUID> idsProdutosComLotesDivergentes(@Param("idEstabelecimento") UUID idEstabelecimento);
}
