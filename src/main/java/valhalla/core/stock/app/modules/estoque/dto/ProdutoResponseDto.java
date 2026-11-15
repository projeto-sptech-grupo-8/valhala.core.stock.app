package valhalla.core.stock.app.modules.estoque.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        Boolean fardo,
        Integer unidadesPorFardo,
        Boolean ativo,
        BigDecimal quantidadeEstoque,
        BigDecimal estoqueMinimo,
        String lote,
        LocalDate validade,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {
    public ProdutoResponseDto(
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
            Boolean fardo,
            Integer unidadesPorFardo,
            Boolean ativo,
            BigDecimal quantidadeEstoque,
            BigDecimal estoqueMinimo,
            LocalDateTime criadoEm,
            LocalDateTime atualizadoEm
    ) {
        this(id, sku, nome, descricao, tipo, categoriaId, categoriaNome, unidadeMedida,
                codigoBarras, precoCusto, precoVenda, margemPercentual, fracionado,
                volumeEmbalagemMl, fardo, unidadesPorFardo, ativo, quantidadeEstoque,
                estoqueMinimo, null, null, criadoEm, atualizadoEm);
    }
}
