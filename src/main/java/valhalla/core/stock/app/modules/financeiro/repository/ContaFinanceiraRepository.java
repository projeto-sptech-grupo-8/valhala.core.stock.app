package valhalla.core.stock.app.modules.financeiro.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import valhalla.core.stock.app.modules.financeiro.entity.ContaFinanceiraEntity;

import java.util.Optional;
import java.util.UUID;

public interface ContaFinanceiraRepository
        extends JpaRepository<ContaFinanceiraEntity, UUID>, JpaSpecificationExecutor<ContaFinanceiraEntity> {

    Optional<ContaFinanceiraEntity> findByIdAndEstabelecimentoId(UUID id, UUID estabelecimentoId);

    /** Bloqueia a conta para alteração, baixa ou inativação (evita baixa em dobro). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContaFinanceiraEntity c where c.id = :id and c.estabelecimentoId = :idEstabelecimento")
    Optional<ContaFinanceiraEntity> buscarParaAtualizacao(@Param("id") UUID id,
                                                         @Param("idEstabelecimento") UUID idEstabelecimento);
}
