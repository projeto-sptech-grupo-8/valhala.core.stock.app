package valhalla.core.stock.app.modules.caixa.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import valhalla.core.stock.app.modules.caixa.entity.PagamentoPedidoEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PagamentoPedidoRepository extends JpaRepository<PagamentoPedidoEntity, UUID> {

    List<PagamentoPedidoEntity> findBySessaoCaixaIdOrderByCriadoEmAsc(UUID sessaoCaixaId);

    List<PagamentoPedidoEntity> findByPedidoIdOrderByCriadoEmAsc(UUID pedidoId);

    List<PagamentoPedidoEntity> findByPedidoIdInOrderByCriadoEmAsc(Collection<UUID> pedidoIds);

    Optional<PagamentoPedidoEntity> findByPedidoIdAndChaveIdempotencia(
            UUID pedidoId,
            String chaveIdempotencia
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from PagamentoPedidoEntity p
            where p.id = :id
              and p.pedidoId = :pedidoId
            """)
    Optional<PagamentoPedidoEntity> buscarParaAtualizacao(
            @Param("id") UUID id,
            @Param("pedidoId") UUID pedidoId
    );
}
