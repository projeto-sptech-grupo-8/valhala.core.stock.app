package valhalla.core.stock.app.modules.accesscontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.accesscontrol.entity.FuncionalidadeEntity;

public interface FuncionalidadeRepository extends JpaRepository<FuncionalidadeEntity, Integer> {
}
