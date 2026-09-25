package valhalla.core.stock.app.modules.accesscontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.accesscontrol.entity.FuncionalidadeEntity;

import java.util.Collection;
import java.util.List;

public interface FuncionalidadeRepository extends JpaRepository<FuncionalidadeEntity, Integer> {
    List<FuncionalidadeEntity> findByCodeIn(Collection<String> codes);
}
