package valhalla.core.stock.app.modules.caixa.configuracao.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.CaixaEntity;

import java.util.Optional;
import java.util.UUID;

public interface CaixaRepository
        extends JpaRepository<CaixaEntity, UUID>, JpaSpecificationExecutor<CaixaEntity> {

    Optional<CaixaEntity> findByIdAndEstabelecimentoId(UUID id, UUID estabelecimentoId);

    boolean existsByEstabelecimentoIdAndNomeIgnoreCase(UUID estabelecimentoId, String nome);

    boolean existsByEstabelecimentoIdAndNomeIgnoreCaseAndIdNot(
            UUID estabelecimentoId,
            String nome,
            UUID id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CaixaEntity c where c.id = :id and c.estabelecimentoId = :estabelecimentoId")
    Optional<CaixaEntity> buscarParaAtualizacao(
            @Param("id") UUID id,
            @Param("estabelecimentoId") UUID estabelecimentoId
    );
}
