package valhalla.core.stock.app.modules.estoque.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Produto do estabelecimento autenticado")
public record ProdutoResponseDto(
        UUID id,
        String sku,
        String nome,
        String descricao,
        ProdutoTipo tipo,
        Integer categoriaId,
        String categoriaNome,
        String unidadeMedida,
        String codigoBarras,
        BigDecimal precoCusto,
        BigDecimal precoVenda,
        BigDecimal margemPercentual,
        Boolean fracionado,
        BigDecimal volumeEmbalagemMl,
        Boolean ativo,
        BigDecimal quantidadeEstoque,
        BigDecimal estoqueMinimo,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {
}
