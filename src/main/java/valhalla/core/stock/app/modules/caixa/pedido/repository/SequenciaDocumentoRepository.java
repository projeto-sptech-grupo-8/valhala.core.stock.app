package valhalla.core.stock.app.modules.caixa.pedido.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import valhalla.core.stock.app.modules.caixa.pedido.entity.SequenciaDocumentoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.SequenciaDocumentoId;

import java.util.Optional;
import java.util.UUID;

public interface SequenciaDocumentoRepository
        extends JpaRepository<SequenciaDocumentoEntity, SequenciaDocumentoId>,
                SequenciaDocumentoRepositoryCustom {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from SequenciaDocumentoEntity s
            where s.estabelecimentoId = :estabelecimentoId
              and s.tipo = :tipo
            """)
    Optional<SequenciaDocumentoEntity> buscarParaAtualizacao(
            @Param("estabelecimentoId") UUID estabelecimentoId,
            @Param("tipo") String tipo
    );
}
