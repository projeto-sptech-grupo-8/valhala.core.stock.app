package valhalla.core.stock.app.modules.caixa.configuracao.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.TaxaPagamentoEntity;

import java.util.Optional;
import java.util.UUID;

public interface TaxaPagamentoRepository
        extends JpaRepository<TaxaPagamentoEntity, UUID>, JpaSpecificationExecutor<TaxaPagamentoEntity> {

    Optional<TaxaPagamentoEntity> findByIdAndEstabelecimentoId(UUID id, UUID estabelecimentoId);

    Optional<TaxaPagamentoEntity> findByEstabelecimentoIdAndFormaAndParcelasAndAtivoTrue(
            UUID estabelecimentoId,
            FormaPagamentoCaixa forma,
            int parcelas
    );

    boolean existsByEstabelecimentoIdAndFormaAndParcelas(
            UUID estabelecimentoId,
            FormaPagamentoCaixa forma,
            int parcelas
    );
}
