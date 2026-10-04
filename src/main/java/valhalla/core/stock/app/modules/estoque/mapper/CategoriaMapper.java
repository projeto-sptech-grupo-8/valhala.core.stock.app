package valhalla.core.stock.app.modules.estoque.mapper;

import valhalla.core.stock.app.modules.estoque.dto.CategoriaResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;

public final class CategoriaMapper {

    private CategoriaMapper() {
    }

    public static CategoriaResponseDto paraResposta(
            CategoriaEntity categoria
    ) {
        return new CategoriaResponseDto(
                categoria.getId(),
                categoria.getNome(),
                categoria.getDescricao(),
                categoria.isAtivo(),
                categoria.getCriadoEm(),
                categoria.getAtualizadoEm()
        );
    }
}
