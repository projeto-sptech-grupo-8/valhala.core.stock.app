package valhalla.core.stock.app.modules.caixa.point.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import valhalla.core.stock.app.modules.caixa.point.entity.EventoWebhookPagamentoEntity;

import java.util.Optional;

public interface EventoWebhookPagamentoRepository extends JpaRepository<EventoWebhookPagamentoEntity, Long> {

    Optional<EventoWebhookPagamentoEntity> findByProvedorAndEventoExternoId(String provedor, String eventoExternoId);

    Optional<EventoWebhookPagamentoEntity> findByProvedorAndRequestId(String provedor, String requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from EventoWebhookPagamentoEntity e
            where e.provedor = :provedor
              and e.eventoExternoId = :eventoExternoId
            """)
    Optional<EventoWebhookPagamentoEntity> buscarPorProvedorEEventoParaAtualizacao(
            @Param("provedor") String provedor,
            @Param("eventoExternoId") String eventoExternoId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventoWebhookPagamentoEntity e where e.id = :id")
    Optional<EventoWebhookPagamentoEntity> buscarPorIdParaAtualizacao(@Param("id") Long id);
}
