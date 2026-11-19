package valhalla.core.stock.app.modules.caixa.fiscal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import valhalla.core.stock.app.modules.caixa.fiscal.entity.NotaFiscalEntity;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.StatusNotaFiscal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotaFiscalRepository
        extends JpaRepository<NotaFiscalEntity, UUID>, JpaSpecificationExecutor<NotaFiscalEntity> {

    Optional<NotaFiscalEntity> findByIdAndEstabelecimentoId(UUID id, UUID estabelecimentoId);

    Optional<NotaFiscalEntity> findFirstByPedidoIdAndStatusOrderByCriadaEmDesc(
            UUID pedidoId,
            StatusNotaFiscal status
    );

    List<NotaFiscalEntity> findByPedidoIdOrderByCriadaEmDesc(UUID pedidoId);

    List<NotaFiscalEntity> findByPedidoIdInOrderByCriadaEmDesc(Collection<UUID> pedidoIds);

    boolean existsByPedidoIdAndStatus(UUID pedidoId, StatusNotaFiscal status);
}
