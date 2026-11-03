package valhalla.core.stock.app.modules.caixa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.caixa.entity.PagamentoPedidoEntity;

import java.util.List;
import java.util.UUID;

public interface PagamentoPedidoRepository extends JpaRepository<PagamentoPedidoEntity, UUID> {

    List<PagamentoPedidoEntity> findBySessaoCaixaIdOrderByCriadoEmAsc(UUID sessaoCaixaId);
}
