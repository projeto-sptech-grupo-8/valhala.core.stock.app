package valhalla.core.stock.app.modules.caixa.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import valhalla.core.stock.app.modules.caixa.entity.SessaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.StatusSessaoCaixa;

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

    @Query("""
            select s
            from SessaoCaixaEntity s
            where s.estabelecimentoId = :estabelecimentoId
              and s.status = :status
              and coalesce(s.terminalId, 'SEM_TERMINAL') = :terminalKey
            order by s.abertaEm desc
            """)
    List<SessaoCaixaEntity> buscarPorEstabelecimentoStatusETerminal(
            @Param("estabelecimentoId") UUID estabelecimentoId,
            @Param("status") StatusSessaoCaixa status,
            @Param("terminalKey") String terminalKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessaoCaixaEntity s where s.id = :id")
    Optional<SessaoCaixaEntity> buscarParaAtualizacao(@Param("id") UUID id);
}
