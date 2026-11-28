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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LoteEstoqueRepository extends JpaRepository<LoteEstoqueEntity, Long>, JpaSpecificationExecutor<LoteEstoqueEntity> {

    List<LoteEstoqueEntity> findAllByEstoqueId(Integer idEstoque);

    @Override
    @EntityGraph(attributePaths = {"estoque", "estoque.produto"})
    Page<LoteEstoqueEntity> findAll(Specification<LoteEstoqueEntity> specification, Pageable pageable);

    @Query("""
            select count(l) as lotesComSaldo,
                   coalesce(sum(case when l.validade is not null and l.validade < :hoje then 1 else 0 end), 0) as vencidos,
                   coalesce(sum(case when l.validade is not null and l.validade >= :hoje and l.validade <= :em7Dias then 1 else 0 end), 0) as vencemEm7Dias,
                   coalesce(sum(case when l.validade is not null and l.validade > :em7Dias and l.validade <= :em30Dias then 1 else 0 end), 0) as vencemEm30Dias,
                   coalesce(sum(case when l.validade is null then 1 else 0 end), 0) as semValidade,
                   coalesce(sum(case
                       when l.validade is not null and l.validade < :hoje then
                           case
                               when p.fracionado = true and p.volumeEmbalagemMl is not null and p.volumeEmbalagemMl > 0
                                   then (l.quantidadeAtual * coalesce(p.precoCusto, cast(0 as bigdecimal))) / p.volumeEmbalagemMl
                               else l.quantidadeAtual * coalesce(p.precoCusto, cast(0 as bigdecimal))
                           end
                       else cast(0 as bigdecimal) end), cast(0 as bigdecimal)) as valorVencido,
                   coalesce(sum(case
                       when l.validade is not null and l.validade <= :em30Dias then
                           case
                               when p.fracionado = true and p.volumeEmbalagemMl is not null and p.volumeEmbalagemMl > 0
                                   then (l.quantidadeAtual * coalesce(p.precoCusto, cast(0 as bigdecimal))) / p.volumeEmbalagemMl
                               else l.quantidadeAtual * coalesce(p.precoCusto, cast(0 as bigdecimal))
                           end
                       else cast(0 as bigdecimal) end), cast(0 as bigdecimal)) as valorEmRisco
            from LoteEstoqueEntity l
            join l.estoque e
            join e.produto p
            where p.estabelecimentoId = :idEstabelecimento
              and p.ativo = true
              and l.quantidadeAtual > 0
            """)
    ResumoValidadeLotesProjection resumirValidade(
            @Param("idEstabelecimento") UUID idEstabelecimento,
            @Param("hoje") LocalDate hoje,
            @Param("em7Dias") LocalDate em7Dias,
            @Param("em30Dias") LocalDate em30Dias
    );

    interface ResumoValidadeLotesProjection {
        Long getLotesComSaldo();

        Long getVencidos();

        Long getVencemEm7Dias();

        Long getVencemEm30Dias();

        Long getSemValidade();

        BigDecimal getValorVencido();

        BigDecimal getValorEmRisco();
    }
}
