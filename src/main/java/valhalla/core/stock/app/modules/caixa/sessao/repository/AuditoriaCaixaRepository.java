package valhalla.core.stock.app.modules.caixa.sessao.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import valhalla.core.stock.app.modules.caixa.sessao.entity.AuditoriaCaixaEntity;

public interface AuditoriaCaixaRepository
        extends JpaRepository<AuditoriaCaixaEntity, Long>, JpaSpecificationExecutor<AuditoriaCaixaEntity> {
}
