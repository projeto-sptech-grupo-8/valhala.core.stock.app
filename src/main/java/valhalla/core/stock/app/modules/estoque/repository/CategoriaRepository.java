package valhalla.core.stock.app.modules.estoque.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;

import java.util.List;
import java.util.UUID;

public interface CategoriaRepository extends JpaRepository<CategoriaEntity, Integer> {

    List<CategoriaEntity> findAllByEstabelecimentoIdAndAtivoTrueOrderByNomeAsc(UUID idEstabelecimento);

    boolean existsByEstabelecimentoIdAndNomeIgnoreCase(UUID idEstabelecimento, String nome);

    boolean existsByEstabelecimentoIdAndNomeIgnoreCaseAndIdNot(
            UUID idEstabelecimento,
            String nome,
            Integer idCategoria
    );
}
