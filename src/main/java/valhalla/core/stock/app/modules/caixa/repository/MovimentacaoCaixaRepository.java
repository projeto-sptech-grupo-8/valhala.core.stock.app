package valhalla.core.stock.app.modules.caixa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.caixa.entity.MovimentacaoCaixaEntity;

import java.util.List;
import java.util.UUID;

public interface MovimentacaoCaixaRepository extends JpaRepository<MovimentacaoCaixaEntity, Long> {

    List<MovimentacaoCaixaEntity> findBySessaoCaixaIdOrderByOcorridoEmAsc(UUID sessaoCaixaId);
}
