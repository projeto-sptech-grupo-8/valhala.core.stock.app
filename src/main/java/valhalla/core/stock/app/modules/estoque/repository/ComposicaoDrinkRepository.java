package valhalla.core.stock.app.modules.estoque.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.estoque.entity.ComposicaoDrinkEntity;

import java.util.List;
import java.util.UUID;

public interface ComposicaoDrinkRepository extends JpaRepository<ComposicaoDrinkEntity, Long> {

    @EntityGraph(attributePaths = "produto")
    List<ComposicaoDrinkEntity> findAllByDrinkIdOrderByIdAsc(UUID idDrink);

    void deleteAllByDrinkId(UUID idDrink);
}
