package valhalla.core.stock.app.modules.estoque.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;

import java.util.Collection;
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

    @Override
    @EntityGraph(attributePaths = {"estoque", "estoque.produto"})
    List<MovimentacaoEstoqueEntity> findAll(
            Specification<MovimentacaoEstoqueEntity> specification,
            Sort sort
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
            select extract(year from m.ocorridoEm) as ano,
                   extract(month from m.ocorridoEm) as mes,
                   coalesce(sum(case when m.tipo in :tiposEntrada then 1 else 0 end), 0) as entradas,
                   coalesce(sum(case when m.tipo in :tiposSaida then 1 else 0 end), 0) as saidas
            from MovimentacaoEstoqueEntity m
            join m.estoque e
            join e.produto p
            where p.estabelecimentoId = :idEstabelecimento
              and m.ocorridoEm >= :inicio
              and m.ocorridoEm < :fimExclusivo
            group by extract(year from m.ocorridoEm), extract(month from m.ocorridoEm)
            order by extract(year from m.ocorridoEm), extract(month from m.ocorridoEm)
            """)
    List<ResumoMovimentacaoMensalDashboardProjection> resumirMensalParaDashboard(
            @Param("idEstabelecimento") UUID idEstabelecimento,
            @Param("inicio") LocalDateTime inicio,
            @Param("fimExclusivo") LocalDateTime fimExclusivo,
            @Param("tiposEntrada") Collection<TipoMovimentacaoEstoque> tiposEntrada,
            @Param("tiposSaida") Collection<TipoMovimentacaoEstoque> tiposSaida
    );

    @EntityGraph(attributePaths = {"estoque", "estoque.produto"})
    List<MovimentacaoEstoqueEntity> findAllByItemPedidoIdInAndTipoInOrderByIdAsc(
            Collection<Long> itemPedidoIds,
            Collection<TipoMovimentacaoEstoque> tipos
    );

    boolean existsByEstoqueProdutoId(UUID produtoId);

    boolean existsByOrigemDrinkId(UUID origemDrinkId);

    /**
     * Saídas por venda e consumo desde o instante informado, por produto: SAIDA e SAIDA_DRINK menos os
     * estornos de vendas canceladas (AJUSTE_POSITIVO vinculado a item de pedido).
     */
    @Query("""
            select e.produto.id as produtoId,
                   coalesce(sum(case
                       when m.tipo in (valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque.SAIDA,
                                       valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque.SAIDA_DRINK)
                           then m.quantidade
                       else -m.quantidade end), 0) as quantidade
            from MovimentacaoEstoqueEntity m
            join m.estoque e
            where e.produto.id in :idsProdutos
              and m.ocorridoEm >= :desde
              and (m.tipo in (valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque.SAIDA,
                              valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque.SAIDA_DRINK)
                   or (m.tipo = valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque.AJUSTE_POSITIVO
                       and m.itemPedidoId is not null))
            group by e.produto.id
            """)
    List<SaidasProdutoProjection> somarSaidasPorProduto(
            @Param("idsProdutos") java.util.Collection<UUID> idsProdutos,
            @Param("desde") LocalDateTime desde
    );

    /** Contagem por tipo no estabelecimento; estornos de venda (ajuste com item de pedido) separados. */
    @Query("""
            select m.tipo as tipo,
                   case when m.itemPedidoId is null then false else true end as vinculadaVenda,
                   count(m) as quantidade
            from MovimentacaoEstoqueEntity m
            join m.estoque e
            join e.produto p
            where p.estabelecimentoId = :idEstabelecimento
              and m.ocorridoEm >= :desde
            group by m.tipo, case when m.itemPedidoId is null then false else true end
            """)
    List<ContagemTipoProjection> contarPorTipo(
            @Param("idEstabelecimento") UUID idEstabelecimento,
            @Param("desde") LocalDateTime desde
    );

    interface ContagemTipoProjection {
        valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque getTipo();

        Boolean getVinculadaVenda();

        Long getQuantidade();
    }

    interface SaidasProdutoProjection {
        UUID getProdutoId();

        java.math.BigDecimal getQuantidade();
    }
}
