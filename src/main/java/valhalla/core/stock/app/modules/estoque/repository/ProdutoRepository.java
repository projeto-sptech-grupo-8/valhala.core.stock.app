package valhalla.core.stock.app.modules.estoque.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProdutoRepository extends JpaRepository<ProdutoEntity, UUID>, JpaSpecificationExecutor<ProdutoEntity> {

    @Override
    @EntityGraph(attributePaths = "categoria")
    Page<ProdutoEntity> findAll(Specification<ProdutoEntity> specification, Pageable pageable);

    @EntityGraph(attributePaths = "categoria")
    List<ProdutoEntity> findAllByEstabelecimentoIdOrderByNomeAsc(UUID idEstabelecimento);

    @EntityGraph(attributePaths = "categoria")
    Optional<ProdutoEntity> findByIdAndEstabelecimentoId(UUID id, UUID idEstabelecimento);

    boolean existsByEstabelecimentoIdAndSku(UUID idEstabelecimento, String sku);
}
