package valhalla.core.stock.app.modules.caixa.configuracao.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.TerminalCaixaEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TerminalCaixaRepository extends JpaRepository<TerminalCaixaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TerminalCaixaEntity t where t.id = :id")
    Optional<TerminalCaixaEntity> buscarParaAtualizacao(@Param("id") UUID id);

    List<TerminalCaixaEntity> findAllByCaixaIdOrderByNomeAsc(UUID caixaId);

    List<TerminalCaixaEntity> findAllByCaixaIdInOrderByNomeAsc(Collection<UUID> caixaIds);

    Optional<TerminalCaixaEntity> findByIdAndCaixaId(UUID id, UUID caixaId);

    boolean existsByTerminalId(String terminalId);

    boolean existsByCaixaIdAndNomeIgnoreCase(UUID caixaId, String nome);

    boolean existsByCaixaIdAndNomeIgnoreCaseAndIdNot(UUID caixaId, String nome, UUID id);

    void deleteAllByCaixaId(UUID caixaId);

    @Query("""
            select count(t)
            from TerminalCaixaEntity t
            where t.caixa.estabelecimentoId = :estabelecimentoId
              and t.caixa.ativo = true
              and t.ativo = true
            """)
    long contarAtivosDoEstabelecimento(@Param("estabelecimentoId") UUID estabelecimentoId);
}
