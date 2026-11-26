package valhalla.core.stock.app.modules.caixa.pedido.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ItemPedidoEntity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ItemPedidoRepository extends JpaRepository<ItemPedidoEntity, Long> {

    List<ItemPedidoEntity> findByPedidoIdOrderByIdAsc(UUID pedidoId);

    List<ItemPedidoEntity> findByPedidoIdInOrderByIdAsc(Collection<UUID> pedidoIds);

    boolean existsByProdutoId(UUID produtoId);

    void deleteByPedidoId(UUID pedidoId);
}
