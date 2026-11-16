package valhalla.core.stock.app.modules.caixa.sessao.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import valhalla.core.stock.app.modules.caixa.sessao.entity.SessaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.sessao.enums.StatusSessaoCaixa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessaoCaixaRepository
        extends JpaRepository<SessaoCaixaEntity, UUID>, JpaSpecificationExecutor<SessaoCaixaEntity> {

    Optional<SessaoCaixaEntity> findByIdAndEstabelecimentoId(UUID id, UUID estabelecimentoId);

    Optional<SessaoCaixaEntity> findFirstByEstabelecimentoIdAndOperadorUsuarioIdAndStatusOrderByAbertaEmDesc(
            UUID estabelecimentoId,
            UUID operadorUsuarioId,
            StatusSessaoCaixa status
    );

    boolean existsByEstabelecimentoIdAndOperadorUsuarioIdAndStatus(
            UUID estabelecimentoId,
            UUID operadorUsuarioId,
            StatusSessaoCaixa status
    );

    boolean existsByCaixaId(UUID caixaId);

    boolean existsByCaixaIdAndStatus(UUID caixaId, StatusSessaoCaixa status);

    @Query("""
            select s.caixa.id
            from SessaoCaixaEntity s
            where s.estabelecimentoId = :estabelecimentoId
              and s.status = :status
            """)
    List<UUID> buscarCaixaIdsPorStatus(
            @Param("estabelecimentoId") UUID estabelecimentoId,
            @Param("status") StatusSessaoCaixa status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessaoCaixaEntity s where s.id = :id")
    Optional<SessaoCaixaEntity> buscarParaAtualizacao(@Param("id") UUID id);
}
