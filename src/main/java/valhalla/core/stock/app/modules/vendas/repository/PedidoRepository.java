package valhalla.core.stock.app.modules.vendas.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import valhalla.core.stock.app.modules.vendas.entity.PedidoEntity;

import java.util.Optional;
import java.util.UUID;

public interface PedidoRepository extends JpaRepository<PedidoEntity, UUID>, JpaSpecificationExecutor<PedidoEntity> {

    /**
     * Serializa a numeração das vendas do estabelecimento: quem obtém o bloqueio calcula o próximo número
     * e só o libera ao fim da transação, evitando números duplicados em vendas simultâneas.
     */
    @Query(value = "SELECT id FROM estabelecimento WHERE id = :idEstabelecimento FOR UPDATE", nativeQuery = true)
    Optional<UUID> bloquearEstabelecimento(@Param("idEstabelecimento") UUID idEstabelecimento);

    @Query("select coalesce(max(p.numero), 0) from PedidoEntity p where p.estabelecimentoId = :idEstabelecimento")
    long ultimoNumero(@Param("idEstabelecimento") UUID idEstabelecimento);

    @EntityGraph(attributePaths = {"itens"})
    Optional<PedidoEntity> findByIdAndEstabelecimentoId(UUID id, UUID estabelecimentoId);

    /** Bloqueia a venda para o cancelamento, evitando estorno em dobro em cancelamentos simultâneos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PedidoEntity p where p.id = :id and p.estabelecimentoId = :idEstabelecimento")
    Optional<PedidoEntity> buscarParaAtualizacao(@Param("id") UUID id,
                                                 @Param("idEstabelecimento") UUID idEstabelecimento);

    @Override
    Page<PedidoEntity> findAll(Specification<PedidoEntity> specification, Pageable pageable);
}
