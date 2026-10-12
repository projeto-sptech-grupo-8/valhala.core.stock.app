package valhalla.core.stock.app.modules.estoque.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoEstoqueEntity;

import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.repository.query.Param;

public interface MovimentacaoEstoqueRepository extends JpaRepository<MovimentacaoEstoqueEntity, Long>, JpaSpecificationExecutor<MovimentacaoEstoqueEntity> {

    @Override
    @EntityGraph(attributePaths = {"estoque", "estoque.produto"})
    Page<MovimentacaoEstoqueEntity> findAll(
            Specification<MovimentacaoEstoqueEntity> specification,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"estoque", "estoque.produto"})
    List<MovimentacaoEstoqueEntity> findAllByEstoqueProdutoEstabelecimentoIdOrderByOcorridoEmDesc(
            UUID idEstabelecimento
    );

    @EntityGraph(attributePaths = {"estoque", "estoque.produto", "estoque.produto.categoria"})
    @Query("""
            select m from MovimentacaoEstoqueEntity m
            join m.estoque e
            join e.produto p
            where p.estabelecimentoId = :idEstabelecimento
            order by m.ocorridoEm desc
            """)
    List<MovimentacaoEstoqueEntity> listarRecentesParaDashboard(
            @Param("idEstabelecimento") UUID idEstabelecimento,
            Pageable pageable
    );

    @Query("""
            select function('year', m.ocorridoEm) as ano,
                   function('month', m.ocorridoEm) as mes,
                   coalesce(sum(case when m.tipo in :tiposEntrada then 1 else 0 end), 0) as entradas,
                   coalesce(sum(case when m.tipo in :tiposSaida then 1 else 0 end), 0) as saidas
            from MovimentacaoEstoqueEntity m
            join m.estoque e
            join e.produto p
            where p.estabelecimentoId = :idEstabelecimento
              and m.ocorridoEm >= :inicio
              and m.ocorridoEm < :fimExclusivo
            group by function('year', m.ocorridoEm), function('month', m.ocorridoEm)
            order by function('year', m.ocorridoEm), function('month', m.ocorridoEm)
            """)
    List<ResumoMovimentacaoMensalDashboardProjection> resumirMensalParaDashboard(
            @Param("idEstabelecimento") UUID idEstabelecimento,
            @Param("inicio") LocalDateTime inicio,
            @Param("fimExclusivo") LocalDateTime fimExclusivo,
            @Param("tiposEntrada") java.util.Collection<valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque> tiposEntrada,
            @Param("tiposSaida") java.util.Collection<valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque> tiposSaida
    );

    boolean existsByEstoqueProdutoId(UUID produtoId);
}
