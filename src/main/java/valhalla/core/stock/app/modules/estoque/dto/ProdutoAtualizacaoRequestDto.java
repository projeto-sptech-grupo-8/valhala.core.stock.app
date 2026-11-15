package valhalla.core.stock.app.modules.estoque.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Dados parciais para atualização de um produto. SKU e saldo não são alteráveis por este endpoint.")
public record ProdutoAtualizacaoRequestDto(
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        String nome,
        Integer categoriaId,
        @Size(max = 10, message = "A unidade de medida deve ter no máximo 10 caracteres")
        String unidadeMedida,
        @Positive(message = "O preço de venda deve ser maior que zero")
        BigDecimal precoVenda,
        @DecimalMin(value = "0.0", message = "O preço de custo não pode ser negativo")
        BigDecimal precoCusto,
        Boolean fracionado,
        @Positive(message = "O volume da embalagem deve ser maior que zero")
        BigDecimal volumeEmbalagemMl,
        @PositiveOrZero(message = "O estoque mínimo não pode ser negativo")
        BigDecimal estoqueMinimo,
        Boolean fardo,
        @Min(value = 2, message = "Um fardo deve possuir ao menos 2 unidades")
        Integer unidadesPorFardo,
        Boolean ativo
) {
    public ProdutoAtualizacaoRequestDto(
            String nome,
            Integer categoriaId,
            String unidadeMedida,
            BigDecimal precoVenda,
            BigDecimal precoCusto,
            Boolean fracionado,
            BigDecimal volumeEmbalagemMl,
            BigDecimal estoqueMinimo,
            Boolean ativo
    ) {
        this(nome, categoriaId, unidadeMedida, precoVenda, precoCusto,
                fracionado, volumeEmbalagemMl, estoqueMinimo, null, null, ativo);
    }

    @AssertTrue(message = "Informe ao menos um campo para atualização")
    public boolean isAtualizacaoValida() {
        return nome != null || categoriaId != null || unidadeMedida != null
                || precoVenda != null || precoCusto != null || fracionado != null
                || volumeEmbalagemMl != null || estoqueMinimo != null
                || fardo != null || unidadesPorFardo != null || ativo != null;
    }
}
