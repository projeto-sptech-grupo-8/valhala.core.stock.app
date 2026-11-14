package valhalla.core.stock.app.modules.caixa.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import valhalla.core.stock.app.modules.caixa.entity.ReservaEstoqueEntity;
import valhalla.core.stock.app.modules.caixa.entity.StatusReservaEstoque;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ReservaEstoqueRepository extends JpaRepository<ReservaEstoqueEntity, Long> {

    List<ReservaEstoqueEntity> findByPedidoIdOrderByIdAsc(UUID pedidoId);

    List<ReservaEstoqueEntity> findByPedidoIdAndStatusOrderByIdAsc(UUID pedidoId, StatusReservaEstoque status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM ReservaEstoqueEntity r
            WHERE r.pedidoId = :pedidoId
              AND r.status = :status
            ORDER BY r.id ASC
            """)
    List<ReservaEstoqueEntity> buscarPorPedidoEStatusParaAtualizacao(
            @Param("pedidoId") UUID pedidoId,
            @Param("status") StatusReservaEstoque status
    );

    @Query("""
            SELECT DISTINCT r.pedidoId
            FROM ReservaEstoqueEntity r
            WHERE r.status = valhalla.core.stock.app.modules.caixa.entity.StatusReservaEstoque.ATIVA
              AND r.expiraEm <= :instanteLimite
            """)
    List<UUID> listarPedidosComReservasExpiradas(@Param("instanteLimite") LocalDateTime instanteLimite);
}
