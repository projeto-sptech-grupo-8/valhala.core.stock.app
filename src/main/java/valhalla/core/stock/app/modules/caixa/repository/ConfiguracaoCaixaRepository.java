package valhalla.core.stock.app.modules.caixa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.caixa.entity.ConfiguracaoCaixaEntity;

import java.util.Optional;
import java.util.UUID;

public interface ConfiguracaoCaixaRepository extends JpaRepository<ConfiguracaoCaixaEntity, UUID> {

    Optional<ConfiguracaoCaixaEntity> findByEstabelecimentoId(UUID estabelecimentoId);
}
