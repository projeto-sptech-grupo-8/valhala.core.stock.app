package valhalla.core.stock.app.modules.estoque.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoEstoqueEntity;

import java.util.List;
import java.util.UUID;

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

    boolean existsByEstoqueProdutoId(UUID produtoId);
}
