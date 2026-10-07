package valhalla.core.stock.app.modules.estoque.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
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

    long deleteByProdutoId(UUID idProduto);
}
