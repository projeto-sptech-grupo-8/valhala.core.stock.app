package valhalla.core.stock.app.modules.caixa.configuracao.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.ConfiguracaoCaixaEntity;

import java.util.Optional;
import java.util.UUID;

public interface ConfiguracaoCaixaRepository extends JpaRepository<ConfiguracaoCaixaEntity, UUID> {

    Optional<ConfiguracaoCaixaEntity> findByEstabelecimentoId(UUID estabelecimentoId);
}
