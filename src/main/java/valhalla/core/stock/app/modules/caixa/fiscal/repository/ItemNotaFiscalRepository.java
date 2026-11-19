package valhalla.core.stock.app.modules.caixa.fiscal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.caixa.fiscal.entity.ItemNotaFiscalEntity;

import java.util.List;
import java.util.UUID;

public interface ItemNotaFiscalRepository extends JpaRepository<ItemNotaFiscalEntity, Long> {

    List<ItemNotaFiscalEntity> findByNotaFiscalIdOrderByIdAsc(UUID notaFiscalId);
}
