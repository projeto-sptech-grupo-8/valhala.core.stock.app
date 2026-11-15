package valhalla.core.stock.app.modules.caixa.pedido.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;

import java.util.Optional;
import java.util.UUID;

public interface PedidoRepository
        extends JpaRepository<PedidoEntity, UUID>, JpaSpecificationExecutor<PedidoEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from PedidoEntity p
            where p.id = :id
              and p.estabelecimentoId = :estabelecimentoId
            """)
    Optional<PedidoEntity> buscarParaAtualizacao(
            @Param("id") UUID id,
            @Param("estabelecimentoId") UUID estabelecimentoId
    );
}
