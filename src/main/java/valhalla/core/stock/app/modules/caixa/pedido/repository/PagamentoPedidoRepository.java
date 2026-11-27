package valhalla.core.stock.app.modules.caixa.pedido.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPagamentoPedido;

import java.time.LocalDateTime;
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

    Optional<PagamentoPedidoEntity> findByOrderExternaId(String orderExternaId);

    Optional<PagamentoPedidoEntity> findByReferenciaExterna(String referenciaExterna);

    @Query("select p.pedidoId from PagamentoPedidoEntity p where p.id = :id")
    Optional<UUID> buscarPedidoIdPorPagamentoId(@Param("id") UUID id);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PagamentoPedidoEntity p where p.orderExternaId = :orderExternaId")
    Optional<PagamentoPedidoEntity> buscarPorOrderExternaParaAtualizacao(
            @Param("orderExternaId") String orderExternaId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PagamentoPedidoEntity p where p.referenciaExterna = :referenciaExterna")
    Optional<PagamentoPedidoEntity> buscarPorReferenciaExternaParaAtualizacao(
            @Param("referenciaExterna") String referenciaExterna
    );

    @Query("""
            select count(p) > 0
            from PagamentoPedidoEntity p
            where p.terminalId = :terminalId
              and p.status in :statusAtivos
            """)
    boolean existePagamentoAtivoNoTerminal(
            @Param("terminalId") String terminalId,
            @Param("statusAtivos") Collection<StatusPagamentoPedido> statusAtivos
    );

    @Query("""
            select count(p) > 0
            from PagamentoPedidoEntity p
            where p.terminalCaixa.caixa.id = :caixaId
               or p.terminalId in (
                   select t.terminalId
                   from TerminalCaixaEntity t
                   where t.caixa.id = :caixaId
               )
            """)
    boolean existePagamentoPorCaixaId(@Param("caixaId") UUID caixaId);

    @Query("""
            select p
            from PagamentoPedidoEntity p
            where p.provedor = 'MERCADO_PAGO'
              and p.orderExternaId is not null
              and p.status in :statusPendentes
              and p.solicitadoEm <= :limiteSolicitadoEm
            order by p.solicitadoEm asc
            """)
    List<PagamentoPedidoEntity> buscarPagamentosPointParaReconciliacao(
            @Param("statusPendentes") Collection<StatusPagamentoPedido> statusPendentes,
            @Param("limiteSolicitadoEm") LocalDateTime limiteSolicitadoEm
    );
}
